use aws_lambda_events::sqs::SqsEventObj;
use lambda_runtime::LambdaEvent;
use serde::{Deserialize, Serialize};

use crate::characters::{SerifGenerator, SlackDummyUser};

mod characters;

#[tokio::main]
async fn main() -> Result<(), lambda_runtime::Error> {
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .without_time()
        .init();

    lambda_runtime::run(lambda_runtime::service_fn(handler)).await
}

#[derive(Deserialize, Serialize, Debug)]
pub struct Args {}

async fn handler(e: LambdaEvent<SqsEventObj<Args>>) -> Result<(), lambda_runtime::Error> {
    tracing::info!(data = ?e, "handle events");

    let actor = characters::Koyuki;
    let message = actor.generate_message(&mut rand::thread_rng());
    let mut post_req = async_slack_web_api::api::chat::post_message::Request {
        channel: env!("SLACK_TARGET_CHANNEL"),
        text: Some(&message),
        ..Default::default()
    };
    actor.modify_post_request(&mut post_req);
    async_slack_web_api::api::chat::PostMessage
        .send(env!("SLACK_BOT_TOKEN"), post_req)
        .await?;

    Ok(())
}
