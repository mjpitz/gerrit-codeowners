package io.storj.gerrit.plugins.codeowners;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.GitRepositoryManager;
import io.storj.codeowners.Config;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReviewAssignerTest {

    @Test
    public void test() throws Exception {
        GitHub gitHub = Mockito.mock(GitHub.class);
        GitRepositoryManager git = Mockito.mock(GitRepositoryManager.class);


        GerritApi gerritApi = Mockito.mock(GerritApi.class);

        Accounts accounts = Mockito.mock(Accounts.class);
        Mockito.when(gerritApi.accounts()).thenReturn(accounts);


        addMockUser(accounts, "bela", "bela@storj.io", 1);
        addMockUser(accounts, "elek", "elek@storj.io", 2);
        addMockUser(accounts, "admin", "admin@storj.io", 3);

        PluginConfigFactory configFactory = Mockito.mock(PluginConfigFactory.class);
        Mockito.when(configFactory.getFromGerritConfig("codeowners")).thenReturn(
                PluginConfig.create("codeowners", new org.eclipse.jgit.lib.Config(), null));
        ReviewAssigner assigner = new ReviewAssigner(configFactory, gitHub, gerritApi, git);

        Config c = Config.open(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("TEST_CODEOWNERS2")));
        Set<String> files = new HashSet<>();
        files.add("README.md");

        Set<Integer> integers = assigner.fromCodeOwners(c, files);

        Set<Integer> expected = new HashSet<>();
        expected.add(1);
        expected.add(2);
        expected.add(3);

        Assert.assertEquals(expected, integers);
    }

    private void addMockUser(Accounts accounts, String name, String email, Integer id) throws RestApiException {
        List<AccountInfo> res = new ArrayList<>();
        AccountInfo user = new AccountInfo(name, email);
        user._accountId = id;
        res.add(user);

        Accounts.QueryRequest query = Mockito.mock(Accounts.QueryRequest.class);
        Mockito.when(query.get()).thenReturn(res);
        Mockito.when(accounts.query("username:" + name)).thenReturn(query);
        Mockito.when(accounts.query("email:" + email)).thenReturn(query);
    }

}
