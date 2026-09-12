import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from '@/stores/auth'
import { PageLoader } from '@/components/ui/Misc'
import { Layout } from '@/components/app/Layout'
import LoginPage from '@/features/auth/LoginPage'
import ExecutiveDashboard from '@/features/dashboard/ExecutiveDashboard'
import MyDayPage from '@/features/dashboard/MyDayPage'
import LeadsPage from '@/features/leads/LeadsPage'
import LeadDetailPage from '@/features/leads/LeadDetailPage'
import PipelinePage from '@/features/pipeline/PipelinePage'
import DealsPage from '@/features/deals/DealsPage'
import TasksPage from '@/features/tasks/TasksPage'
import ChatPage from '@/features/chat/ChatPage'
import CallsPage from '@/features/calls/CallsPage'
import CompaniesPage from '@/features/companies/CompaniesPage'
import ContactsPage from '@/features/contacts/ContactsPage'
import ClientsPage from '@/features/clients/ClientsPage'
import EmailsPage from '@/features/emails/EmailsPage'
import TemplatesPage from '@/features/emails/TemplatesPage'
import AccountsPage from '@/features/emails/AccountsPage'
import SuppressionsPage from '@/features/emails/SuppressionsPage'
import CampaignsPage from '@/features/campaigns/CampaignsPage'
import CampaignDetailPage from '@/features/campaigns/CampaignDetailPage'
import MeetingsPage from '@/features/meetings/MeetingsPage'
import ProposalsPage, { ProposalDetail } from '@/features/proposals/ProposalsPage'
import ReportsPage from '@/features/reports/ReportsPage'
import DocumentsPage from '@/features/documents/DocumentsPage'
import UsersPage from '@/features/admin/UsersPage'
import TeamsPage from '@/features/admin/TeamsPage'
import RolesPage from '@/features/admin/RolesPage'
import PipelinesPage from '@/features/admin/PipelinesPage'
import TagsSourcesPage from '@/features/admin/TagsSourcesPage'
import CustomFieldsPage from '@/features/admin/CustomFieldsPage'
import ScoringPage from '@/features/admin/ScoringPage'
import AutomationsPage from '@/features/admin/AutomationsPage'
import AuditPage from '@/features/admin/AuditPage'
import OrgSettingsPage from '@/features/admin/OrgSettingsPage'

export default function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return <div className="flex h-screen items-center justify-center bg-slate-100"><PageLoader /></div>
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route path="/login" element={<Navigate to="/dashboard" replace />} />
      <Route element={<Layout />}>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<ExecutiveDashboard />} />
        <Route path="/my-day" element={<MyDayPage />} />
        <Route path="/leads" element={<LeadsPage />} />
        <Route path="/leads/:id" element={<LeadDetailPage />} />
        <Route path="/pipeline" element={<PipelinePage />} />
        <Route path="/deals" element={<DealsPage />} />
        <Route path="/tasks" element={<TasksPage />} />
        <Route path="/calls" element={<CallsPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/companies" element={<CompaniesPage />} />
        <Route path="/companies/:id" element={<CompaniesPage />} />
        <Route path="/contacts" element={<ContactsPage />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/emails" element={<EmailsPage />} />
        <Route path="/emails/templates" element={<TemplatesPage />} />
        <Route path="/emails/accounts" element={<AccountsPage />} />
        <Route path="/emails/suppressions" element={<SuppressionsPage />} />
        <Route path="/campaigns" element={<CampaignsPage />} />
        <Route path="/campaigns/:id" element={<CampaignDetailPage />} />
        <Route path="/meetings" element={<MeetingsPage />} />
        <Route path="/proposals" element={<ProposalsPage />} />
        <Route path="/proposals/:id" element={<ProposalDetail />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/documents" element={<DocumentsPage />} />
        <Route path="/admin/users" element={<UsersPage />} />
        <Route path="/admin/teams" element={<TeamsPage />} />
        <Route path="/admin/roles" element={<RolesPage />} />
        <Route path="/admin/pipelines" element={<PipelinesPage />} />
        <Route path="/admin/tags-sources" element={<TagsSourcesPage />} />
        <Route path="/admin/custom-fields" element={<CustomFieldsPage />} />
        <Route path="/admin/scoring" element={<ScoringPage />} />
        <Route path="/admin/automations" element={<AutomationsPage />} />
        <Route path="/admin/audit" element={<AuditPage />} />
        <Route path="/admin/settings" element={<OrgSettingsPage />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  )
}
