use regex::RegexSet;
use crate::app_state::AppState;

#[cfg(target_os = "android")]
const FILE_PROVIDER_IDS: [&str; 5] = [
    r"com.android.externalstorage.documents",
    r"com.android.providers.downloads.documents",
    r"com.google.android.apps.docs.storage",
    r"com.dropbox.product.android.dbapp.document_provider.documents",
    r"com.microsoft.skydrive.content.StorageAccessProvider",
];

#[cfg(target_os = "android")]
const FILE_PROVIDER_NAMES: [&str; 5] = [
    "On My Device",
    "Downloads",
    "Google Drive",
    "Dropbox",
    "OneDrive",
];

#[cfg(target_os = "android")]
pub fn extract_file_provider(full_file_name_uri: &str) -> String {
    let re = RegexSet::new(&FILE_PROVIDER_IDS).unwrap();
    let matches: Vec<_> = re.matches(full_file_name_uri).into_iter().collect();
    log::debug!("Matches {:?}, {:?}", matches, matches.first());

    let location_name = matches
        .first()
        .and_then(|i| FILE_PROVIDER_NAMES.get(*i))
        .map_or("Cloud storage / Another app", |s| s);

    location_name.to_string()
}
