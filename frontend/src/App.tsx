import { lazy, Suspense, type ReactNode } from 'react'
import { Spinner } from './components/ui'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { useSession } from './hooks/useAuth'
import AppLayout from './layouts/AppLayout'
import ClubLayout from './layouts/ClubLayout'
import Landing from './pages/Landing'
import { ForgotPassword, Login, Register, ResetPassword, VerifyEmail } from './pages/Auth'
import Verify from './pages/Verify'
import NotFound from './pages/NotFound'
import Home from './pages/app/Home'
import ClubPublic from './pages/app/ClubPublic'
import PublicEvent from './pages/app/PublicEvent'
import ApplyDrive from './pages/app/ApplyDrive'
import Notifications from './pages/app/Notifications'
import Platform from './pages/app/Platform'
import WhatsOn from './pages/campus/WhatsOn'
import Clubs from './pages/campus/Clubs'
import Recruiting from './pages/campus/Recruiting'
import ClubListing from './pages/campus/ClubListing'
import Moderation from './pages/campus/Moderation'
import Dashboard from './pages/club/Dashboard'
import Events from './pages/club/Events'
import EventManage from './pages/club/EventManage'
import Recruitment from './pages/club/Recruitment'
import Pipeline from './pages/club/Pipeline'
import Members from './pages/club/Members'
import Audit from './pages/club/Audit'
import Settings from './pages/club/Settings'

// The scanner pulls in the ZXing decoder (~400 kB); only door volunteers need it.
const Scanner = lazy(() => import('./pages/club/Scanner'))

function RequireAuth({ children }: { children: ReactNode }) {
  const signedIn = !!useSession()
  const location = useLocation()
  if (!signedIn) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

function GuestOnly({ children }: { children: ReactNode }) {
  return useSession() ? <Navigate to="/app" replace /> : children
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route path="/forgot-password" element={<GuestOnly><ForgotPassword /></GuestOnly>} />
      {/* email links work whether or not this browser is signed in */}
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/verify/:slug/:id" element={<Verify />} />

      <Route path="/app" element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route index element={<Home />} />
        <Route path="events" element={<WhatsOn />} />
        <Route path="clubs" element={<Clubs />} />
        <Route path="recruiting" element={<Recruiting />} />
        <Route path="directory/:slug" element={<ClubListing />} />
        <Route path="moderation" element={<Moderation />} />
        {/* the old ClubHub-only directory: the campus directory covers every club now */}
        <Route path="explore" element={<Navigate to="/app/clubs" replace />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="platform" element={<Platform />} />
        <Route path="clubs/:slug/events/:eventId" element={<PublicEvent />} />
        <Route path="clubs/:slug/recruitment/drives/:driveId" element={<ApplyDrive />} />
        <Route path="clubs/:slug/*" element={<ClubPublic />} />

        <Route path="c/:slug" element={<ClubLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="events" element={<Events />} />
          <Route path="events/:eventId" element={<EventManage />} />
          <Route path="events/:eventId/scan" element={<Suspense fallback={<div className="grid place-items-center py-24"><Spinner /></div>}><Scanner /></Suspense>} />
          <Route path="recruitment" element={<Recruitment />} />
          <Route path="recruitment/:driveId" element={<Pipeline />} />
          <Route path="members" element={<Members />} />
          <Route path="audit" element={<Audit />} />
          <Route path="settings" element={<Settings />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
