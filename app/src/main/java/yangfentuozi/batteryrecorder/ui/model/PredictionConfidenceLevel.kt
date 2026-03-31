package yangfentuozi.batteryrecorder.ui.model

/**
 * 首页预测卡片展示使用的置信度档位。
 *
 * 该档位只服务于首页颜色表达，不参与预测算法计算。
 */
enum class PredictionConfidenceLevel {
    Low,
    Medium,
    High
}
