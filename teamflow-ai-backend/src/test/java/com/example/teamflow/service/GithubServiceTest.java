package com.example.teamflow.service;

import com.example.teamflow.ai.AiClient;
import com.example.teamflow.common.BizException;
import com.example.teamflow.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class GithubServiceTest {

    @Mock
    private ProjectService projectService;
    @Mock
    private SectionService sectionService;
    @Mock
    private AiClient aiClient;

    @InjectMocks
    private GithubService githubService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetBranchInfoNoRepo() {
        Project mockProject = new Project();
        mockProject.setId(1L);
        mockProject.setGithubRepo(null);

        when(projectService.getProject(1L)).thenReturn(mockProject);

        BizException exception = assertThrows(BizException.class, () -> {
            githubService.getBranchInfo(1L, "FRONTEND");
        });

        assertEquals(400, exception.getCode());
        assertEquals("项目未绑定 GitHub 仓库，请先在项目设置中绑定。", exception.getMessage());
    }

    @Test
    void testGetBranchInfoNoToken() {
        Project mockProject = new Project();
        mockProject.setId(1L);
        mockProject.setGithubRepo("owner/repo");

        when(projectService.getProject(1L)).thenReturn(mockProject);
        ReflectionTestUtils.setField(githubService, "token", "");

        BizException exception = assertThrows(BizException.class, () -> {
            githubService.getBranchInfo(1L, "FRONTEND");
        });

        assertEquals(400, exception.getCode());
        assertEquals("GitHub 令牌未配置，请配置环境变量 GITHUB_TOKEN。", exception.getMessage());
    }

    @Test
    void testCleanRepoName() {
        assertEquals("DexLey0819/TeamAIFlow", ReflectionTestUtils.invokeMethod(githubService, "cleanRepoName", "DexLey0819/TeamAIFlow.git"));
        assertEquals("DexLey0819/TeamAIFlow", ReflectionTestUtils.invokeMethod(githubService, "cleanRepoName", "https://github.com/DexLey0819/TeamAIFlow.git"));
        assertEquals("DexLey0819/TeamAIFlow", ReflectionTestUtils.invokeMethod(githubService, "cleanRepoName", "git@github.com:DexLey0819/TeamAIFlow.git"));
        assertEquals("DexLey0819/TeamAIFlow", ReflectionTestUtils.invokeMethod(githubService, "cleanRepoName", "  https://github.com/DexLey0819/TeamAIFlow/  "));
        assertEquals("DexLey0819/TeamAIFlow", ReflectionTestUtils.invokeMethod(githubService, "cleanRepoName", "repos/DexLey0819/TeamAIFlow"));
        assertEquals("DexLey0819/TeamAIFlow", ReflectionTestUtils.invokeMethod(githubService, "cleanRepoName", "github.com/repos/DexLey0819/TeamAIFlow"));
    }
}
