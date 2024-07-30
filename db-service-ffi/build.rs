fn main() {
    uniffi::generate_scaffolding("./src/db_service.udl").unwrap();

    // Using println! to see output in build.rs will not work

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
