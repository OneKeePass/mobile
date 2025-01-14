

// Called to create an async function ('send_*') that in turn calls the corresponding
// internal async fn and send the result back in the passed oneshot send channel
// See 'receive_from_async_fn' macros where we create the oneshot channel and call the 'send_*' aync fn in a 'spawn' call

// Args are
// store   - determines to use sftp or webdav connections store
// fn_name - This is the name of the async funtion that is created
// arg1    - The arguments for that function
// channel_ret_val  - The type of the value returned in the channel in the inner async fn call
// inner_fn_name - This is the inner async funtion is that is called in turn for the actual operation and its return
//                 value if of type 'channel_ret_val'
// arg2    - The arguments for the inner function

#[macro_export]
macro_rules! reply_by_async_fn {
    ($store:ident, $fn_name:ident ($($arg1:tt:$arg_type:ty),*),$inner_fn_name:tt ($($arg2:expr),*),$channel_ret_val:ty) => {
        pub(crate) async fn $fn_name(
            tx: oneshot::Sender<Result<$channel_ret_val>>,
            connetion_name:String,
            $($arg1:$arg_type),*

        ) {

            log::debug!("In send async fn {} ", stringify!($fn_name));

            let connections = $store().lock().await;

            let r = if let Some(conn) = connections.get(&connetion_name) {
                // e.g conn.connect_to_server(connection_info)
                conn.$inner_fn_name($($arg2),*).await
            } else {
                Err(error::Error::UnexpectedError(format!(
                    "No previous connected session is found for the connection name {}",
                    connetion_name
                )))
            };

            let r = tx.send(r);
            if let Err(_) = r {
                let name = stringify!($fn_name);
                // Should not happen? But may happen if no receiver?
                log::error!("The '{}' fn send channel call failed ", &name);
            }
        }
    };
}

// Called to create a block where we create an oneshot channel with (receiver,sender) and
// then calls the 'async fn send_*' passed in 'aync_fn_name'
// path detrmines whether to use 'SftpConnection' or 'WebdavConnection'
// channel_ret_val  - The type of the value returned in the channel
// aync_fn_name - This is the async funtion (of pattern like send_*) that is called in 'async_runtime().spawn()'
// arg - arguments for 'aync_fn_name'
#[macro_export]
macro_rules! receive_from_async_fn {
    ($path:ident::$aync_fn_name:ident ($($arg:tt),*),$channel_ret_val:ty) => {{
        let (tx, rx) = oneshot::channel::<Result<$channel_ret_val>>();
        
        //log::debug!("One shot channel is created for aync_fn_name {} with args {} ", &stringify!($aync_fn_name),stringify!(($($arg),*)));
        
        async_runtime().spawn($path::$aync_fn_name(tx, $($arg),*));
        let s = rx.blocking_recv().map_err(|e| {
            let name = stringify!($aync_fn_name);
            error::Error::UnexpectedError(format!(
                "Receive channel error {} when calling inner async fn {} ", e,&name
            ))
        });
        s
    }};
}

// TODO:  We may see 'unnecessary parentheses around pattern' when we use a single 'Some'
// Need to fix those situations
// Need to use #[allow(unused_parens)] in the call site

// Called to extract the ref of Option fields found in 'self'
#[macro_export]
macro_rules! parse_operation_fields_if {
    ($self:expr,$($field_vals:tt),*) => {
        if let ($(Some($field_vals)),*) = ($($self.$field_vals.as_ref()),*) {
            ($($field_vals),*)
        } else {
            return Err(error::Error::DataError("Required fields are not found"))
        }
    };
}
