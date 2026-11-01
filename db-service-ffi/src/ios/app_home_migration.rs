// App home dir move (v0.25.0): this whole module. Keep it while users may still update from a
// version before v0.25.0, as they can skip versions
// Moves the main app's internal dirs and files from its previous home dir to the current one.
// The main app's home dir was the app sandbox's 'Documents' and is now 'AppGroup/okp_main'.
// Swift passes the location not in use as 'previous_app_home_dir', so the move works in both
// directions and a Debug build can switch between the two

use std::{
    fs,
    path::{Path, PathBuf},
};

use crate::{
    app_preference::PREFERENCE_JSON_FILE_NAME,
    app_state::{BACKUPS_DIR, EXPORT_DATA_DIR, KEY_FILES_DIR, REMOTE_STORAGE_DIR},
    ios::bookmark::BOOK_MARK_FILES_DIR,
};

// Only these are moved. Anything else found in the previous home dir (e.g 'Documents/Inbox'
// that iOS manages) is left untouched.
// 'preference.json' and 'key_files' are found there only on devices that have not run a
// version since they were moved to 'okp_shared'. They are moved along so that the existing
// moves in AppState setup still find them in the app home dir
pub(crate) const MIGRATED_ITEMS: [&str; 6] = [
    BOOK_MARK_FILES_DIR,
    BACKUPS_DIR,
    EXPORT_DATA_DIR,
    REMOTE_STORAGE_DIR,
    PREFERENCE_JSON_FILE_NAME,
    KEY_FILES_DIR,
];

#[derive(Debug, Default, PartialEq)]
pub(crate) struct MigrationReport {
    pub(crate) moved: Vec<String>,
    // Found in both places with content in the current one. The previous one is left as it is
    pub(crate) conflicts: Vec<String>,
    // Item name and the error. The previous one is left as it is and is tried again on next launch
    pub(crate) failed: Vec<(String, String)>,
}

impl MigrationReport {
    pub(crate) fn is_empty(&self) -> bool {
        self.moved.is_empty() && self.conflicts.is_empty() && self.failed.is_empty()
    }
}

// Each item is moved with a rename, which is a single step as both home dirs are on the same
// volume. An item already moved is not found in the previous dir any more, so a run that was
// interrupted completes on the next call
pub(crate) fn migrate_app_home(previous: &Path, current: &Path) -> MigrationReport {
    let mut report = MigrationReport::default();

    if previous == current || !previous.is_dir() {
        return report;
    }

    if let Err(e) = fs::create_dir_all(current) {
        for name in MIGRATED_ITEMS {
            if previous.join(name).exists() {
                report.failed.push((name.to_string(), e.to_string()));
            }
        }
        return report;
    }

    for name in MIGRATED_ITEMS {
        let src = previous.join(name);
        // symlink_metadata so that a dangling link is also seen as present
        if fs::symlink_metadata(&src).is_err() {
            continue;
        }

        let dst = current.join(name);
        if fs::symlink_metadata(&dst).is_ok() {
            // An earlier app setup in the current dir leaves empty dirs such as 'backups/history'
            // and 'remote_storage/sftp'. Those are replaced. Anything holding a file is never
            // merged or overwritten
            if dst.is_dir() && !has_any_file(&dst) {
                if let Err(e) = fs::remove_dir_all(&dst) {
                    report.failed.push((name.to_string(), e.to_string()));
                    continue;
                }
            } else {
                report.conflicts.push(name.to_string());
                continue;
            }
        }

        match fs::rename(&src, &dst) {
            Ok(()) => report.moved.push(name.to_string()),
            Err(e) => report.failed.push((name.to_string(), e.to_string())),
        }
    }

    report
}

// True when there is at least one non dir entry anywhere under this dir. A read error is
// taken as 'has a file' so that nothing is removed when we can not be sure it is empty
fn has_any_file(dir: &Path) -> bool {
    let Ok(entries) = fs::read_dir(dir) else {
        return true;
    };
    for entry in entries {
        let Ok(entry) = entry else {
            return true;
        };
        let path: PathBuf = entry.path();
        match entry.file_type() {
            Ok(t) if t.is_dir() => {
                if has_any_file(&path) {
                    return true;
                }
            }
            _ => return true,
        }
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    // A test's own root dir under the temp dir, removed when dropped
    struct TestRoot(PathBuf);

    impl TestRoot {
        fn new() -> Self {
            let p = std::env::temp_dir()
                .join("okp_app_home_migration_tests")
                .join(uuid::Uuid::new_v4().to_string());
            fs::create_dir_all(&p).unwrap();
            TestRoot(p)
        }

        fn dir(&self, name: &str) -> PathBuf {
            self.0.join(name)
        }
    }

    impl Drop for TestRoot {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    fn write(root: &Path, rel: &str, content: &str) {
        let p = root.join(rel);
        fs::create_dir_all(p.parent().unwrap()).unwrap();
        fs::write(p, content).unwrap();
    }

    fn mkdirs(root: &Path, rel: &str) {
        fs::create_dir_all(root.join(rel)).unwrap();
    }

    // Relative path -> file content for every file, and "<dir>" for every dir, under root
    fn snapshot(root: &Path) -> BTreeMap<String, String> {
        fn walk(root: &Path, dir: &Path, out: &mut BTreeMap<String, String>) {
            for entry in fs::read_dir(dir).unwrap() {
                let path = entry.unwrap().path();
                let rel = path.strip_prefix(root).unwrap().to_string_lossy().to_string();
                if path.is_dir() {
                    out.insert(rel, "<dir>".into());
                    walk(root, &path, out);
                } else {
                    out.insert(rel, fs::read_to_string(&path).unwrap());
                }
            }
        }
        let mut out = BTreeMap::new();
        if root.exists() {
            walk(root, root, &mut out);
        }
        out
    }

    // A Documents dir as an existing install has it
    fn full_documents_layout(root: &Path) {
        write(root, "bookmarks/1234567", "bookmark-data");
        write(root, "bookmarks/7654321", "bookmark-data-2");
        write(root, "backups/history/111/db_1700000000.kdbx", "backup-1");
        write(root, "backups/history/111/db_1700000100.kdbx", "backup-2");
        write(root, "backups/history/222/other_1700000200.kdbx", "backup-3");
        write(root, "export_data/db.kdbx", "export");
        write(root, "remote_storage/rs_storage_configs.enc", "encrypted-configs");
        write(root, "remote_storage/sftp/conn-1/id_ed25519", "private-key");
    }

    fn names(v: &[&str]) -> Vec<String> {
        v.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn verify_no_previous_dir_is_no_op() {
        let t = TestRoot::new();
        let current = t.dir("current");
        mkdirs(&current, "backups/history");

        let before = snapshot(&current);
        let report = migrate_app_home(&t.dir("does_not_exist"), &current);

        assert!(report.is_empty());
        assert_eq!(snapshot(&current), before);
    }

    #[test]
    fn verify_same_dir_is_no_op() {
        let t = TestRoot::new();
        let home = t.dir("home");
        full_documents_layout(&home);

        let before = snapshot(&home);
        let report = migrate_app_home(&home, &home);

        assert!(report.is_empty());
        assert_eq!(snapshot(&home), before);
    }

    #[test]
    fn verify_full_layout_is_moved() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        full_documents_layout(&previous);

        let before = snapshot(&previous);
        let report = migrate_app_home(&previous, &current);

        assert_eq!(
            report.moved,
            names(&["bookmarks", "backups", "export_data", "remote_storage"])
        );
        assert!(report.conflicts.is_empty());
        assert!(report.failed.is_empty());
        assert_eq!(snapshot(&current), before);
        assert!(snapshot(&previous).is_empty());
    }

    #[test]
    fn verify_round_trip_restores_layout() {
        let t = TestRoot::new();
        let a = t.dir("Documents");
        let b = t.dir("okp_main");
        full_documents_layout(&a);

        let before = snapshot(&a);
        migrate_app_home(&a, &b);
        let report = migrate_app_home(&b, &a);

        assert_eq!(report.moved.len(), 4);
        assert_eq!(snapshot(&a), before);
        assert!(snapshot(&b).is_empty());
    }

    #[test]
    fn verify_interrupted_run_completes() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        full_documents_layout(&previous);
        let before = snapshot(&previous);

        // As if the earlier run was killed after moving the first two items
        fs::create_dir_all(&current).unwrap();
        fs::rename(previous.join("bookmarks"), current.join("bookmarks")).unwrap();
        fs::rename(previous.join("backups"), current.join("backups")).unwrap();

        let report = migrate_app_home(&previous, &current);

        assert_eq!(report.moved, names(&["export_data", "remote_storage"]));
        assert!(report.conflicts.is_empty());
        assert_eq!(snapshot(&current), before);
    }

    #[test]
    fn verify_empty_shells_are_replaced() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        full_documents_layout(&previous);
        let before = snapshot(&previous);

        // What an app setup in the current dir creates before any data is written
        mkdirs(&current, "backups/history");
        mkdirs(&current, "remote_storage/sftp");
        mkdirs(&current, "export_data");

        let report = migrate_app_home(&previous, &current);

        assert_eq!(report.moved.len(), 4);
        assert!(report.conflicts.is_empty());
        assert_eq!(snapshot(&current), before);
    }

    #[test]
    fn verify_conflict_leaves_both_sides() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        write(&previous, "backups/history/111/db_1.kdbx", "old");
        write(&previous, "bookmarks/1", "bm");
        write(&current, "backups/history/222/db_2.kdbx", "new");

        let report = migrate_app_home(&previous, &current);

        assert_eq!(report.conflicts, names(&["backups"]));
        assert_eq!(report.moved, names(&["bookmarks"]));
        assert_eq!(
            fs::read_to_string(previous.join("backups/history/111/db_1.kdbx")).unwrap(),
            "old"
        );
        assert_eq!(
            fs::read_to_string(current.join("backups/history/222/db_2.kdbx")).unwrap(),
            "new"
        );
        assert!(!current.join("backups/history/111").exists());
    }

    #[test]
    fn verify_file_conflict_is_not_overwritten() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        write(&previous, "preference.json", "old-pref");
        write(&current, "preference.json", "new-pref");

        let report = migrate_app_home(&previous, &current);

        assert_eq!(report.conflicts, names(&["preference.json"]));
        assert_eq!(fs::read_to_string(current.join("preference.json")).unwrap(), "new-pref");
        assert_eq!(fs::read_to_string(previous.join("preference.json")).unwrap(), "old-pref");
    }

    #[test]
    fn verify_old_preference_and_key_files_are_moved() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        write(&previous, "preference.json", "{}");
        write(&previous, "key_files/my.keyx", "key");

        let report = migrate_app_home(&previous, &current);

        assert_eq!(report.moved, names(&["preference.json", "key_files"]));
        assert_eq!(fs::read_to_string(current.join("preference.json")).unwrap(), "{}");
        assert_eq!(fs::read_to_string(current.join("key_files/my.keyx")).unwrap(), "key");
    }

    #[test]
    fn verify_unlisted_items_are_untouched() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        full_documents_layout(&previous);
        write(&previous, "Inbox/shared.kdbx", "inbox");
        write(&previous, "other.txt", "other");

        let report = migrate_app_home(&previous, &current);

        assert_eq!(report.moved.len(), 4);
        assert_eq!(fs::read_to_string(previous.join("Inbox/shared.kdbx")).unwrap(), "inbox");
        assert_eq!(fs::read_to_string(previous.join("other.txt")).unwrap(), "other");
        assert!(!current.join("Inbox").exists());
        assert!(!current.join("other.txt").exists());
    }

    #[test]
    fn verify_second_run_is_no_op() {
        let t = TestRoot::new();
        let previous = t.dir("Documents");
        let current = t.dir("okp_main");
        full_documents_layout(&previous);

        migrate_app_home(&previous, &current);
        let after_first = snapshot(&current);
        let report = migrate_app_home(&previous, &current);

        assert!(report.is_empty());
        assert_eq!(snapshot(&current), after_first);
    }
}
