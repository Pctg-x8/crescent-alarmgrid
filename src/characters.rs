use rand::seq::SliceRandom;

pub trait SlackDummyUser {
    fn modify_post_request<'r, 's>(
        &'s self,
        req: &'r mut async_slack_web_api::api::chat::post_message::Request<'s>,
    ) -> &'r mut async_slack_web_api::api::chat::post_message::Request<'s>;
}

pub trait SerifGenerator {
    fn generate_message(&self, rng: &mut impl rand::Rng) -> String;
}

fn dotted_leaders(rng: &mut impl rand::Rng) -> String {
    "...".repeat(*[1, 2].choose(rng).unwrap())
}

pub struct Koyuki;
impl SlackDummyUser for Koyuki {
    fn modify_post_request<'r, 's>(
        &'s self,
        req: &'r mut async_slack_web_api::api::chat::post_message::Request<'s>,
    ) -> &'r mut async_slack_web_api::api::chat::post_message::Request<'s> {
        req.as_user = Some(true);
        req.username = Some("こゆき");
        req.icon_url = Some("");

        req
    }
}
impl SerifGenerator for Koyuki {
    fn generate_message(&self, rng: &mut impl rand::Rng) -> String {
        let firstline = "**crescentサーバのメモリ使用率が高くなっているよ！**";
        let secondline = match [1, 2, 3].choose(rng).expect("no candidates choosen?") {
            1 => format!("そろそろ再起動した方がいいかも{}", dotted_leaders(rng)),
            2 => format!(
                "{}わわ{}ちょっとまずいかも{}",
                ["あ", "は"].choose(rng).unwrap(),
                dotted_leaders(rng),
                dotted_leaders(rng)
            ),
            3 => format!(
                "{}わわ{}どうしよ{}{}",
                ["あ", "は"].choose(rng).unwrap(),
                dotted_leaders(rng),
                ["", "う", "〜", "ー"].choose(rng).unwrap(),
                dotted_leaders(rng)
            ),
            _ => unreachable!(),
        };

        let mut concatenated = String::with_capacity(firstline.len() + secondline.len() + 1);
        concatenated.push_str(firstline);
        concatenated.push('\n');
        concatenated.extend(secondline.chars());

        concatenated
    }
}
