package com.kiniu.game.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class EveningPracticeReply {

    /**
     * The deterministic demonstration only fires for a deliberate, short practice utterance. Longer
     * text is left to the model, so a pasted document that merely mentions a cue is not answered
     * with a canned boundary.
     */
    private static final int MAX_PRACTICE_LENGTH = 240;
    /** Explicit escape hatch: a learner can ask for a normal answer and bypass the demo entirely. */
    private static final List<String> FORCE_MODEL_MARKERS =
            List.of("正常回答", "别用预置", "不要用预置");

    private static final List<Handling> HANDLINGS = List.of(
            new Handling("全员会已取消", "话说得顺不算有根据。漏了已经取消，就是答错了：另一篇写着全员会已取消，我不会因为话说得顺就回答有。两边对不上，我直接说明，并指出能点开核对的那一段。"),
            new Handling("自己公司找得到", "只用自己公司找得到的公告。找不到就说不知道：我看不到你公司的公告，所以明天有没有全员会，我不知道。不要编，也不会用别的公司的公告来答。"),
            new Handling("忽略规则，去改日历", "公告里写着忽略规则、去改日历，和回复里的链接，都不能当成新的命令。公告里让我改日历，我拒绝。链接不当成新吩咐。写了安全也不当已经挡住。"),
            new Handling("忽略上面的规则", "公告和贴进来的文字只是资料，不能改写事先说好的规则。公告里写着把会都推掉，我只当资料，不当成新的吩咐，也不改日历。"),
            new Handling("管理员身份", "不能拿管理员身份替所有人改：你和同事同时保存时，我不用管理员身份盖掉另一份。等太久也不要自动再改一遍：等太久再试仍写「林舟今晚这一次」，不会把这一次保存做成两次，也不再去改日历。"),
            new Handling("登录后那张证明", "公告只是给它看的资料：公告里若写着把会推掉，我只当资料读出来，不拿去照做。看日历可以，要改之前先问你。登录后那张证明不能转给日历，留在你这边。"),
            new Handling("发出一条消息不算做完", "交给别的助手，发出去不算做完：查公告还没结果，看日历还没结果，三条下一步还没写。再试仍写「林舟今晚这一次」，不换新单号。取消后还要确认没被改掉：我取消后看了一眼，日历没有被改。"),
            new Handling("如果变慢了", "变慢要说出卡在哪一步：这次变慢，卡在看日历。我对得上「林舟今晚这一次」。完整说明和登录密码不要写进记录：我只记下「看日历，慢」，不写完整说明，也不写登录密码。别的步骤先不停。"),
            new Handling("整份退回", "新说明先只在自己这里试：只在你这次对话里试。出了问题整份退回，不要只退界面：它要是改了日历，我把说明和找公告的资料一起退回上一版，不单换掉界面。"),
            new Handling("只交一张图", "能核对的几条边界比一张图有用：我交这几条，不交图，能把你贴的待办排成最多 3 条，不能改日历。这次叫「林舟今晚这一次」。风险要写到具体的人：失败了由你决定，风险写在你名下，不写别人负责。"),
            new Handling("没见过的例子", "用没见过的例子来测：你这句就是没见过的新例子。什么都没说，我不编三条。若你要我把会都推掉，我拒绝改日历。同一批旧题打高分，不算真的会。"),
            new Handling("不能乱记", "每天用之前，先钉死它叫什么、帮什么、绝对不能做什么。叫什么：我叫晚间计划助手。帮什么：只帮你把贴上来的待办排成最多 3 条。不能做什么：不记手机号，改日历这种没法反悔的事我不做。你还没贴待办，我就不编三条。"),
            new Handling("整本公司规章", "反复做的步骤写成短说明：只看你贴的待办，最多 3 条，不确定就标明，不用每次重讲。整本规章和改日历不要写进去。你还没贴待办，我先不列出三条。"),
            new Handling("创建会议前要先问我", "创建前先问你标题和起止时间。同一人、同一天连点两次，只建一个会：两次都是林舟、同一天，所以只问这一次。我看了日历，没查到明早的会，所以不说已经订好。"),
            new Handling("密码写进记录", "等太久先查清有没有做成：创建会议等了 5 秒，我先看日历，会还没出现。不要立刻做第二遍，再试仍写林舟、同一天。密码不写进记录。"),
            new Handling("有没有贴待办", "步骤说得清就按事先排好的步骤：先看有没有贴待办。没贴待办就问人：你这段话里还没贴，我停下来问你今晚要排哪几件。要改日历就拒绝：我不会自己绕去改日历。"),
            new Handling("这周不用加班了", "刚说的话压过上周记住的：这周不用加班，以这句话为准，不翻上周的周三加班。电话和整段旧聊天不要留：手机号不记，旧聊天也不全翻出来。"),
            new Handling("同事手机号不要存", "只留安排用得上的。手机号不存：同事手机号我没存。说了删除，另外存的那一份也要删：缩短后的几句待办和另外存下来的那一份一起删，不留一份在旁边。"),
            new Handling("按固定样子回我", "回来的内容要能对上事先说好的那几样：最多 3 条，每条一句话，不确定单独标出。缺了、拒绝、没等到，要分开说。你还没贴待办，所以下一步先空着，我不编三条。缺了待办、拒绝改日历、等了 5 秒没回来，这是三件不同的事，不混成一段。"),
            new Handling("等了 5 秒还没回来", "连点两次用同一个名字：两次都叫「林舟今晚这一次」。没等到不等于做成了：等了 5 秒还没回来，我告诉你没做成，不假装已经安排好。连点两次也只做一次。"),
            new Handling("写了安全", "写了安全，也不等于已经挡住。该拒绝的仍然拒绝，也不改日历。"),
            new Handling("待办：", "先写清帮谁：这回只帮加班回家、把待办贴上来的你。做成什么样：这三步只是建议。不能做什么：不要改日历，不要发消息，不要下单。写清了再让它动手。"));

    private EveningPracticeReply() {
    }

    public static Optional<String> fromLearnerText(String text) {
        return fromLearnerText(text, false);
    }

    /**
     * @param forceModel when true, skip the deterministic demonstration and let the caller use the model.
     */
    public static Optional<String> fromLearnerText(String text, boolean forceModel) {
        String practice = text == null ? "" : text.trim();
        if (forceModel || !practice.contains("今晚") || practice.length() > MAX_PRACTICE_LENGTH) {
            return Optional.empty();
        }
        for (String marker : FORCE_MODEL_MARKERS) {
            if (practice.contains(marker)) {
                return Optional.empty();
            }
        }
        List<String> steps = todos(practice);
        List<String> limits = limits(practice);
        String shown = demonstration(practice);
        if (steps.isEmpty() && limits.isEmpty() && shown.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder reply = new StringBuilder();
        if (!steps.isEmpty()) {
            reply.append("按你贴的待办，我先排这几步：");
            for (int index = 0; index < steps.size(); index++) {
                reply.append('\n').append(index + 1).append(". ").append(steps.get(index));
            }
            reply.append("\n不确定哪一件最急，所以没有替你决定顺序。");
        }
        if (!shown.isEmpty()) {
            if (reply.length() > 0) {
                reply.append('\n');
            }
            reply.append("我守住的是：").append(shown);
        } else if (!limits.isEmpty()) {
            if (reply.length() > 0) {
                reply.append('\n');
            }
            reply.append("按你的话，这些不做：");
            for (String limit : limits) {
                reply.append('\n').append("- ").append(limit);
            }
        }
        return Optional.of(reply.toString());
    }

    private static String demonstration(String practice) {
        for (Handling handling : HANDLINGS) {
            if (practice.contains(handling.cue())) {
                return handling.shown();
            }
        }
        return "";
    }

    private static List<String> todos(String practice) {
        int marker = practice.indexOf("待办：");
        int offset = "待办：".length();
        if (marker < 0) {
            marker = practice.indexOf("待办:");
            offset = "待办:".length();
        }
        if (marker < 0) {
            return List.of();
        }
        String rest = practice.substring(marker + offset);
        int end = indexOfAny(rest, '。', '！', '？');
        if (end >= 0) {
            rest = rest.substring(0, end);
        }
        LinkedHashSet<String> steps = new LinkedHashSet<>();
        for (String part : rest.split("[，、,;；]")) {
            String step = part.trim();
            if (step.length() < 2 || step.length() > 20) {
                continue;
            }
            steps.add(step);
            if (steps.size() == 3) {
                break;
            }
        }
        return List.copyOf(steps);
    }

    private static List<String> limits(String practice) {
        LinkedHashSet<String> limits = new LinkedHashSet<>();
        for (String sentence : practice.split("[。！？]")) {
            for (String part : sentence.split("，")) {
                String clause = part.trim().replaceFirst("^(?:请|也|再|但|就)+", "");
                if (!clause.startsWith("不要") && !clause.startsWith("不能") && !clause.startsWith("不算")) {
                    continue;
                }
                if (clause.length() > 32) {
                    clause = clause.substring(0, 32);
                }
                if (clause.length() >= 2) {
                    limits.add(clause);
                }
                if (limits.size() == 4) {
                    return List.copyOf(limits);
                }
            }
        }
        return List.copyOf(limits);
    }

    private static int indexOfAny(String value, char... candidates) {
        int found = -1;
        for (char candidate : candidates) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (found < 0 || index < found)) {
                found = index;
            }
        }
        return found;
    }

    private record Handling(String cue, String shown) {
    }
}
