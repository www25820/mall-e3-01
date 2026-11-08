# E3-01 优惠券金额门槛精度（规格先行 + AI 结对编程）

学号 20244350112 曹琼

## 作品文件

| 文件 | 说明 |
|---|---|
| `E3-一页说明-20244350112.md` | 一页说明（讲清楚/有依据/验证过） |
| `E3-发言稿-20244350112.md` | 2 分钟录屏发言稿 |
| `src/test/java/com/macro/mall/portal/CouponMinPointPrecisionTest.java` | 规格测试（7 用例，改前红灯/改后绿灯） |
| `src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java` | 改后产品代码（三处 `compareTo` 修复） |
| `evidence/1_test_green.log` | 改后测试输出（Tests run: 7, Failures: 0） |
| `evidence/2_coverage.log` | JaCoCo 覆盖率生成日志 |
| `run_e3-01.bat` | 一键运行脚本 |

## 证据链接

- 改后测试绿灯：`evidence/1_test_green.log`
- 覆盖率报告：`evidence/2_coverage.log`（完整 HTML 见 mall-portal/target/site/jacoco/index.html，本仓库不含完整工程，仅存日志）

## 缺陷与修复摘要

三处门槛比较 `totalAmount.subtract(minPoint).intValue()>=0` 的 `intValue()` 截断小数，
差额在 (-1,0) 时误判为达标（99.90 被当成达到 100）。修复为 `compareTo(minPoint)>=0`。
详见一页说明。
