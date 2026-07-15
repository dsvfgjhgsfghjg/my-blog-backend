# App Auth API 开发任务进度

## 已完成
- [x] 分析现有代码结构
- [x] 确认已实现的 REST API（2.1-2.6）
- [x] 确认已实现的 WebSocket 协议
- [x] 确认已实现的数据库表结构

## 待完成
- [ ] 1. 新增数据库迁移 V23：创建认证历史表 t_auth_history
- [ ] 2. 新增实体类 AuthHistory
- [ ] 3. 新增 Mapper AuthHistoryMapper
- [ ] 4. 新增 DTO 类（UserProfileResponse, AuthHistoryResponse, CipherInfoResponse）
- [ ] 5. 新增 REST API：GET /api/auth/app/cipher-info（加密算法信息）
- [ ] 6. 新增 REST API：GET /api/user/profile（用户个人信息）
- [ ] 7. 新增 REST API：GET /api/auth/history（认证历史）
- [ ] 8. 修改 AppAuthService：新增相关业务方法
- [ ] 9. 修改 AuthWebSocketHandler：记录挑战到数据库
- [ ] 10. 安全增强：登录失败防护 + Challenge 防暴力
