/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Copyright (c) 2026 Vishwajeet Pratap Singh
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise.service;

import recusant_byte.vishwajeet.leetvise.config.LeetViseProperties;
import recusant_byte.vishwajeet.leetvise.excel.ExcelStore;
import recusant_byte.vishwajeet.leetvise.leetcode.LeetCodeClient;
import recusant_byte.vishwajeet.leetvise.leetcode.LeetCodeClient.Profile;
import recusant_byte.vishwajeet.leetvise.model.Submission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ProblemServiceTest {

    @TempDir
    Path dir;

    final LeetCodeClient leetCode = mock(LeetCodeClient.class);
    final Profile profile = new Profile(Map.of("All", 2L, "Easy", 2L), Map.of("Easy", 900L));

    private ProblemService service(String username, String session) {
        LeetViseProperties props = new LeetViseProperties(username, session, "", dir.resolve("t.xlsx"), false);
        when(leetCode.authed()).thenReturn(props.hasSession());
        return new ProblemService(new ExcelStore(props), leetCode, props,
                Clock.fixed(Instant.ofEpochSecond(1_790_000_000L), ZoneOffset.UTC));
    }

    @Test
    void detectsUsernameFromSessionCookie() {
        when(leetCode.signedInUsername()).thenReturn("alice");
        ProblemService s = service("", "cookie");
        assertThat(s.state(null).username()).isEqualTo("alice");
        assertThat(s.state(null).usernameSource()).isEqualTo("session");
        verify(leetCode, times(1)).signedInUsername(); // remembered, not asked on every request
    }

    @Test
    void usernameTypedInTheAppIsKeptInTheSheet() {
        when(leetCode.profile("bob")).thenReturn(profile);
        ProblemService s = service("", "");
        assertThat(s.state(null).username()).isEmpty();

        ProblemService.State st = s.setUsername("https://leetcode.com/u/bob/");
        assertThat(st.username()).isEqualTo("bob");
        assertThat(st.usernameSource()).isEqualTo("sheet");
        assertThat(st.leetCodeTotal()).containsEntry("Easy", 900L);
        assertThat(service("", "").state(null).username()).isEqualTo("bob"); // survives a restart
    }

    @Test
    void showsTheWorkbookPathRelativeToTheProjectFolder() {
        LeetViseProperties props = new LeetViseProperties("me", "", "", Path.of("target", "test-data", "x.xlsx"), false);
        ProblemService s = new ProblemService(new ExcelStore(props), leetCode, props);
        assertThat(s.state(null).excelDisplay()).isEqualTo(Path.of("target", "test-data", "x.xlsx").toString());
        assertThat(service("me", "").state(null).excelDisplay()).isEqualTo(dir.resolve("t.xlsx").toString()); // outside: absolute
    }

    @Test
    void rejectsBadUsernames() {
        ProblemService s = service("", "");
        assertThatThrownBy(() -> s.setUsername("not a name!")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.sync(false)).hasMessageContaining("username");
    }

    @Test
    void syncStoresSubmissionsWithoutDuplicates() {
        when(leetCode.signedInUsername()).thenReturn("alice");
        when(leetCode.profile("alice")).thenReturn(profile);
        when(leetCode.allSolved()).thenReturn(List.of(
                new LeetCodeClient.Solved("two-sum", "1", "Two Sum", "Easy", "Array, Hash Table", 0, 0)));
        when(leetCode.submissions(anyLong())).thenReturn(List.of(
                new Submission(11, 1_789_000_000L, "two-sum", "Two Sum", "Wrong Answer", "Java", "", ""),
                new Submission(12, 1_789_000_100L, "two-sum", "Two Sum", "Accepted", "Java", "", "")));
        ProblemService s = service("", "cookie");

        assertThat(s.sync(false).message()).contains("1 new problem").contains("2 new submissions");
        ProblemService.State again = s.sync(false);
        assertThat(again.submissions()).isEqualTo(2);
        assertThat(again.problems()).singleElement().satisfies(p -> assertThat(p.getLastSolved()).isEqualTo(1_789_000_100L));
        assertThat(s.insights().overview().failed()).isEqualTo(1);
    }
}

// LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
