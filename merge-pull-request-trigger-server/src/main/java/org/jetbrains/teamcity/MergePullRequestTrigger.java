package org.jetbrains.teamcity;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import jetbrains.buildServer.buildTriggers.*;
import jetbrains.buildServer.log.LogUtil;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.CustomDataStorage;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.vcs.BranchSpec;
import jetbrains.buildServer.vcs.RepositoryState;
import jetbrains.buildServer.vcs.VcsRootInstance;
import jetbrains.buildServer.vcs.VcsRootInstanceEx;
import org.jetbrains.annotations.NotNull;

public class MergePullRequestTrigger extends BuildTriggerService {

  private static final Logger LOG = Logger.getInstance(MergePullRequestTrigger.class.getName());

  @NotNull
  @Override
  public String getName() {
    return "mergePullRequestTrigger";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Merge pull-request trigger";
  }

  @NotNull
  @Override
  public String describeTrigger(@NotNull final BuildTriggerDescriptor buildTriggerDescriptor) {
    return "";
  }

  @NotNull
  @Override
  public BuildTriggeringPolicy getBuildTriggeringPolicy() {
    return new PolledBuildTrigger() {
      @Override
      public void triggerBuild(@NotNull PolledTriggerContext ctx) throws BuildTriggerException {
        SBuildType buildType = ctx.getBuildType();
        CustomDataStorage storage = ctx.getCustomDataStorage();
        Set<String> branchesToTrigger = new HashSet<String>();
        for (VcsRootInstance root : buildType.getVcsRootInstances()) {
          BranchSpec branchSpec = ((BuildTypeEx)buildType).getBranchSpec(root);
          RepositoryState state = ((VcsRootInstanceEx)root).getLastUsedState();
          for (Map.Entry<String, String> branchRevision : state.getBranchRevisions().entrySet()) {
            String branch = branchRevision.getKey();
            String currentRevision = branchRevision.getValue();
            String prNum = getPrNum(branch);
            if (prNum != null) {
              String prevRevision = readSavedRevision(storage, root, branch);
              if (!currentRevision.equals(prevRevision)) {
                LOG.debug("Pull request revision updated, VCS root " + LogUtil.describe(root) + ", branch " + branch + ": " + prevRevision + " -> " + currentRevision);
                String prMergeBranch = "refs/pull/" + prNum + "/merge";
                String prMergeBranchRevision = state.getBranchRevisions().get(prMergeBranch);
                if (prMergeBranchRevision != null) {
                  String logicalBranchName = branchSpec.getLogicalBranchName(prMergeBranch);
                  if (logicalBranchName != null) {
                    branchesToTrigger.add(logicalBranchName);
                  }
                } else {
                  LOG.debug("Merge branch of pull request '" + branch + "' is not tracked by VCS root " + LogUtil.describe(root));
                }
                saveRevision(storage, root, branch, currentRevision);
              }
            }
          }
        }

        for (String branch : branchesToTrigger) {
          LOG.debug("Trigger merge pull-request build " + LogUtil.describe(buildType) + ", branch " + branch);
          ((BuildTypeEx)buildType).createBuildPromotion(branch).addToQueue("");
        }
      }


      @Override
      public void triggerActivated(@NotNull PolledTriggerContext ctx) throws BuildTriggerException {
        SBuildType buildType = ctx.getBuildType();
        CustomDataStorage storage = ctx.getCustomDataStorage();
        for (VcsRootInstance root : buildType.getVcsRootInstances()) {
          RepositoryState state = ((VcsRootInstanceEx)root).getLastUsedState();
          for (Map.Entry<String, String> branchRevision : state.getBranchRevisions().entrySet()) {
            String branch = branchRevision.getKey();
            String currentRevision = branchRevision.getValue();
            if (getPrNum(branch) != null) {
              saveRevision(storage, root, branch, currentRevision);
            }
          }
        }
        LOG.debug("Trigger activated for " + LogUtil.describe(buildType));
      }
    };
  }


  /**
   * Returns pull-request number or null if the ref is not refs/pull/../head branch
   * @return see above
   */
  @Nullable
  private String getPrNum(@NotNull String branch) {
    if (branch.startsWith("refs/pull/") && branch.endsWith("/head")) {
      return branch.substring("refs/pull/".length(), branch.length() - "/head".length());
    } else {
      return null;
    }
  }


  private void saveRevision(@NotNull CustomDataStorage storage, @NotNull VcsRootInstance root, @NotNull String vcsBranch, @NotNull String revision) {
    storage.putValue("revision:" + root.getParentId() + ":" + vcsBranch, revision);
  }


  @Nullable
  private String readSavedRevision(@NotNull CustomDataStorage storage, @NotNull VcsRootInstance root, @NotNull String vcsBranch) {
    return storage.getValue("revision:" + root.getParentId() + ":" + vcsBranch);
  }
}
