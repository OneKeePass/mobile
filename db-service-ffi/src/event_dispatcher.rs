use log::debug;
use onekeepass_core::async_service;

use crate::{
    app_state::AppState,
    commands::{ok_json_str, result_json_str},
};

pub(crate) fn init_async_listeners() {
    let mut rx = async_service::init_entry_channels();

    // Need to listen for the periodic update of otp tokens
    async_service::async_runtime().spawn(async move {
        loop {
            //debug!("Going to wait for value...");
            let reply: Option<async_service::AsyncResponse> = rx.recv().await;

            //debug!("Received value as {:?}", &reply);

            // Only valid values are send to UI
            if let Some(ov) = reply {
                match ov {
                    async_service::AsyncResponse::EntryOtpToken(t) => {
                        let json_string = ok_json_str(t);
                        let r = AppState::global()
                            .event_dispatcher
                            .send_otp_update(json_string);
                        debug!("send_otp_update r is {:?}", &r);
                        //window.emit(OTP_TOKEN_UPDATE_EVENT, &t).unwrap();
                    }
                    async_service::AsyncResponse::Tick(t) => {
                        let json_string = ok_json_str(t);
                        let r = AppState::global()
                            .event_dispatcher
                            .send_tick_update(json_string);
                        debug!("send_tick_update r is {:?}", &r);
                        //window.emit("TIMER_EVENT", &t).unwrap();
                    }
                }
            } else {
                debug!("No reply of type 'AsyncResponse' was received in channel");
            }
        }
    });
}
