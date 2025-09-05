@echo off
echo ========================================
echo AI Agent 健康检查系统启动指南
echo ========================================
echo.
echo 1. 自动健康检查功能已配置完成！
echo.
echo 2. 配置说明：
echo    - 每10分钟自动执行一次健康检查
echo    - 结果会记录到应用日志中
echo    - 可通过配置文件控制开关和日志级别
echo.
echo 3. 相关端点：
echo    - Actuator健康检查: http://localhost:8080/actuator/health
echo    - 自定义诊断检查: http://localhost:8080/api/diagnostic/health
echo    - 手动触发检查: http://localhost:8080/api/diagnostic/trigger-health-check (POST)
echo    - 健康监控面板: file:///t:/Java/javacode/Ai_Agent/frontend/health-monitor.html
echo.
echo 4. 配置参数 (application.properties)：
echo    - health-check.enabled=true           # 启用健康检查
echo    - health-check.log-level=INFO         # 日志级别
echo    - health-check.cron=0 */10 * * * ?    # 执行频率
echo.
echo 5. 查看方式：
echo    - 查看应用日志文件
echo    - 打开健康监控面板 (推荐)
echo    - 通过API端点获取实时状态
echo.
echo ========================================
echo 启动应用后访问以下URL查看健康监控面板：
echo file:///t:/Java/javacode/Ai_Agent/frontend/health-monitor.html
echo ========================================
pause