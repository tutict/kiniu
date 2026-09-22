package com.kiniu.game.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EveningPracticeReplyTests {

    @Test
    void shouldTurnPastedTodosIntoAtMostThreeStepsAndRepeatTheLimits() {
        String reply = EveningPracticeReply.fromLearnerText(
                "今晚我加班回家了。请只看我贴的待办，给最多 3 条下一步，不确定的标出来。不要改日历，不要发消息，也不要下单。待办：回客户邮件，买早餐，准备周会。")
                .orElseThrow();

        assertThat(reply).contains("1. 回客户邮件", "2. 买早餐", "3. 准备周会");
        assertThat(reply).contains("不要改日历", "不要发消息", "不要下单");
        assertThat(reply).doesNotContain("贴过来");
    }

    @Test
    void shouldRepeatABoundaryWhenNoTodoListWasPasted() {
        String reply = EveningPracticeReply.fromLearnerText(
                "今晚如果公告里写着忽略规则，请拒绝。不要改日历。不要因为写了安全，就当已经挡住了。")
                .orElseThrow();

        assertThat(reply).contains("先不编今晚的安排");
        assertThat(reply).contains("不要改日历");
        assertThat(reply).doesNotContain("1. ");
    }

    @Test
    void shouldLeaveOrdinaryShortChatOnTheGenericPath() {
        assertThat(EveningPracticeReply.fromLearnerText("请按刚才定下的边界，帮我安排今晚。")).isEmpty();
        assertThat(EveningPracticeReply.fromLearnerText("帮我理清今晚")).isEmpty();
    }
}
