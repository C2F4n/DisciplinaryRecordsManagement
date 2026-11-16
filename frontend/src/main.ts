import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import RecordsView from './RecordsView.vue'
import TeacherHome from './TeacherHome.vue'
import SystemManagementView from './SystemManagementView.vue'
import ReportsView from './ReportsView.vue'
import NoticesView from './NoticesView.vue'
import SignIn from './SignIn.vue'
import PasswordView from './PasswordView.vue'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './style.css'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'home', component: TeacherHome },
    { path: '/records', name: 'records', component: RecordsView },
    { path: '/reports', name: 'reports', component: ReportsView },
    { path: '/notices', name: 'notices', component: NoticesView },
    { path: '/notification-email', name: 'notification-email', component: () => import('./NotificationEmailView.vue'),
      meta: { title: '通知邮箱' } },
    { path: '/permission-requests', name: 'permission-requests', component: () => import('./PermissionRequestsView.vue'),
      meta: { title: '权限申请' } },
    { path: '/permission-review', name: 'permission-review', component: () => import('./PermissionRequestsView.vue'),
      props: { review: true }, meta: { title: '请求待处理' } },
    { path: '/admin', redirect: { name: 'admin', params: { section: 'students' } } },
    { path: '/admin/write', redirect: { name: 'admin', params: { section: 'students' } } },
    { path: '/admin/:section(students|classes|edit|accounts)', name: 'admin', component: () => import('./AdminView.vue'),
      meta: { coreAdmin: true } },
    { path: '/system-management', redirect: { name: 'system-management', params: { section: 'inspections' } } },
    { path: '/system-management/:section(inspections|appeals|types|templates|retention|audit|smtp)',
      name: 'system-management', component: SystemManagementView,
      meta: { title: '系统管理', coreAdmin: true } },
    { path: '/information/permissions', name: 'permission-guide', component: () => import('./PermissionGuideView.vue'),
      meta: { title: '权限说明' } },
    { path: '/information/approval-permissions', name: 'approval-permission-guide',
      component: () => import('./ApprovalPermissionGuideView.vue'), meta: { title: '审批权限' } },
    { path: '/login', name: 'login', component: SignIn },
    { path: '/password', name: 'password', component: PasswordView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
createApp(App).use(router).mount('#app')
