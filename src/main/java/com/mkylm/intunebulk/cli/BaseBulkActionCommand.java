package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.ActionOptions;
import com.mkylm.intunebulk.core.ActionRequest;
import com.mkylm.intunebulk.core.ActionResult;
import com.mkylm.intunebulk.core.ActionType;
import com.mkylm.intunebulk.core.DeviceActionService;
import com.mkylm.intunebulk.core.DeviceRef;
import com.mkylm.intunebulk.core.GroupDeviceResolver;
import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.TokenProvider;
import com.mkylm.intunebulk.graph.TokenProviderFactory;
import java.time.Duration;
import java.util.List;
import picocli.CommandLine.Option;

/**
 * Shared command pipeline for bulk subcommands.
 *
 * <p>Every concrete action command (sync/reboot/wipe/autopilot-reset) inherits this flow:
 * parse options -> authenticate -> resolve group devices -> execute action -> render summary.
 */
abstract class BaseBulkActionCommand implements Runnable {
  @Option(
      names = {"--groupId"},
      required = true,
      description = "Azure AD group id (GUID). Devices are resolved via transitive group membership.")
  String groupId;

  @Option(
      names = {"--dryRun"},
      defaultValue = "false",
      description = "Resolve targets and print plan without performing mutations.")
  boolean dryRun;

  @Option(
      names = {"--maxConcurrency"},
      defaultValue = "6",
      description = "Max in-flight device actions.")
  int maxConcurrency;

  @Option(
      names = {"--batchSize"},
      defaultValue = "25",
      description = "Logical batch size for progress reporting.")
  int batchSize;

  @Option(
      names = {"--maxRetries"},
      defaultValue = "6",
      description = "Retries for throttling/transient failures.")
  int maxRetries;

  @Option(
      names = {"--baseBackoffSeconds"},
      defaultValue = "2",
      description = "Base backoff for exponential retry.")
  int baseBackoffSeconds;

  @Option(
      names = {"--useBeta"},
      defaultValue = "true",
      description = "Use Microsoft Graph /beta for endpoints that may not exist in v1.0.")
  boolean useBeta;

  protected abstract ActionType actionType();

  protected ActionOptions buildOptions() {
    // Convert CLI flags into the execution options used by DeviceActionService.
    return ActionOptions.builder()
        .dryRun(dryRun)
        .maxConcurrency(maxConcurrency)
        .batchSize(batchSize)
        .maxRetries(maxRetries)
        .baseBackoff(Duration.ofSeconds(baseBackoffSeconds))
        .continueOnError(true)
        .useBeta(useBeta)
        .build();
  }

  @Override
  public final void run() {
    // 1) Build authenticated Graph client from environment-driven auth settings.
    TokenProvider tokenProvider = TokenProviderFactory.fromEnvironment();
    GraphClient graph = GraphClient.createDefault(tokenProvider);

    // 2) Resolve the group's Entra device members into Intune managedDevice IDs.
    GroupDeviceResolver resolver = new GroupDeviceResolver(graph);
    ResolveProgressBar resolveProgress = new ResolveProgressBar("RESOLVE");
    List<DeviceRef> targets = resolver.resolveFromAadGroup(groupId, resolveProgress::onProgress);

    // 3) Execute the selected action over the resolved targets.
    ActionRequest req = new ActionRequest(actionType(), targets, buildOptions());
    DeviceActionService svc = new DeviceActionService(graph);
    ConsoleProgressBar progressBar = new ConsoleProgressBar(actionType().name());
    List<ActionResult> results = svc.execute(req, progressBar::onProgress);

    // 4) Print operation summary/results for operator feedback.
    BulkOutput.printSummary(req, results);
  }
}

