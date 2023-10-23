fn main() {
    uniffi::generate_scaffolding("./src/db_service.udl").unwrap();
    
    // println!("cargo:rustc-env=BOTAN_CONFIGURE_OS=ios");
    // println!("cargo:rustc-link-lib=static=./libbotan-3.a");
    // println!("cargo:rustc-link-search=/Users/jeyasankar/Development/RustProjects/botan-build/botan-3.1.1/iphone-64/lib");
    // println!("cargo:rustc-link-search=/Users/jeyasankar/Development/RustProjects/botan-build/botan-3.1.1/iphone-simulator/lib");
    //println!("cargo:rustc-link-lib={}", "/Users/jeyasankar/Development/RustProjects/botan-build/botan-3.1.1/iphone-simulator/lib/libbotan-3.a");
    
    /* 
    // uniffi_build::generate_scaffolding("./src/db_service.udl").unwrap();
    // This results in error something like
    // error[internal]: left behind trailing whitespace
    // --> repositories/github/onekeepass/db-service-ffi/target/debug/build/db-service-ffi-aad7d59fc7f80302/out/db_service.uniffi.rs:92:92:1

    // Using disable_all_formatting = true in rustfmt.toml fixes the problem

    // Or we can avoid using unwrap() as done below and that also works
    let _r = uniffi_build::generate_scaffolding("./src/db_service.udl");
    // println!("r is {:?}", r);
    // if let Err(e) = r {
    //      println!( "Some error {:?}", e);
    // }
    */
}
