package com.sophon.core.application;

import com.sophon.bootstrap.SophonBootstrap;
import com.sophon.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 阶段 18：无网络批测回归（Skill/Tool/自检等）；供 {@code --batch-tests} 使用。
 */
public final class BatchRegressionRunner {

    private BatchRegressionRunner() {}

    /** @return 0 成功，非 0 失败 */
    public static int run(AppConfig config) {
        try {
            Path tmp = Files.createTempFile("sophon-batch", ".db");
            tmp.toFile().deleteOnExit();
            try (SophonBootstrap.Handle h = SophonBootstrap.launch(config, tmp)) {
                if (h.tools().definitions().isEmpty()) {
                    return 1;
                }
                if (h.skillRegistry().ids().isEmpty()) {
                    return 2;
                }
                String report = h.doctorService().runReport();
                if (!report.contains("Tool")) {
                    return 3;
                }
                return 0;
            }
        } catch (Exception e) {
            return 99;
        }
    }
}
