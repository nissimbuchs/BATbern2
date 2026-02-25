/**
 * AttendanceTab
 * Story 10.5: Analytics Dashboard (AC3)
 */

import { useTranslation } from 'react-i18next';
import { useAnalyticsAttendance } from '@/hooks/useAnalytics';
import AttendeesPerEventChart from './AttendeesPerEventChart';
import ReturningVsNewChart from './ReturningVsNewChart';

interface Props {
  fromYear?: number;
}

const AttendanceTab = ({ fromYear }: Props) => {
  useTranslation('organizer');
  const { data, isLoading } = useAnalyticsAttendance(fromYear);
  const events = data?.events ?? [];

  return (
    <>
      <AttendeesPerEventChart data={events} isLoading={isLoading} />
      <ReturningVsNewChart data={events} isLoading={isLoading} />
    </>
  );
};

export default AttendanceTab;
