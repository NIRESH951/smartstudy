import { useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  analyticsApi,
  assessmentApi,
  availabilityApi,
  examApi,
  focusApi,
  notificationApi,
  performanceApi,
  plannerApi,
  revisionApi,
  subjectApi,
  topicApi,
} from '../services/api';

// React Query hooks - one per read endpoint. Keys are centralised so mutations can invalidate them.

export const queryKeys = {
  subjects: ['subjects'] as const,
  subject: (id: number) => ['subjects', id] as const,
  topicsBySubject: (subjectId: number) => ['topics', 'subject', subjectId] as const,
  topics: ['topics', 'all'] as const,
  exams: ['exams'] as const,
  readiness: (examId: number) => ['readiness', examId] as const,
  readinessOverview: ['readiness', 'overview'] as const,
  today: ['planner', 'today'] as const,
  calendar: (from: string, to: string) => ['planner', 'calendar', from, to] as const,
  weekly: (date: string) => ['planner', 'weekly', date] as const,
  priorities: ['planner', 'priorities'] as const,
  assessments: ['assessments'] as const,
  weakTopics: ['performance', 'weak'] as const,
  performanceSummary: ['performance', 'summary'] as const,
  topicHistory: (topicId: number) => ['performance', 'history', topicId] as const,
  revisionsDue: ['revisions', 'due'] as const,
  revisionHistory: ['revisions', 'history'] as const,
  focusActive: ['focus', 'active'] as const,
  focusStats: ['focus', 'stats'] as const,
  focusHistory: ['focus', 'history'] as const,
  overview: ['analytics', 'overview'] as const,
  analyticsWeekly: (date: string) => ['analytics', 'weekly', date] as const,
  analyticsSubjects: ['analytics', 'subjects'] as const,
  productivity: (days: number) => ['analytics', 'productivity', days] as const,
  notifications: ['notifications'] as const,
  availability: ['availability'] as const,
};

export const useSubjects = () => useQuery({ queryKey: queryKeys.subjects, queryFn: subjectApi.list });
export const useSubject = (id: number) =>
  useQuery({ queryKey: queryKeys.subject(id), queryFn: () => subjectApi.get(id), enabled: Number.isFinite(id) });
export const useTopicsBySubject = (subjectId: number) =>
  useQuery({
    queryKey: queryKeys.topicsBySubject(subjectId),
    queryFn: () => topicApi.listBySubject(subjectId),
    enabled: Number.isFinite(subjectId),
  });
export const useAllTopics = () => useQuery({ queryKey: queryKeys.topics, queryFn: topicApi.listAll });
export const useExams = () => useQuery({ queryKey: queryKeys.exams, queryFn: () => examApi.list(false) });
export const useReadiness = (examId: number | null) =>
  useQuery({
    queryKey: queryKeys.readiness(examId ?? -1),
    queryFn: () => examApi.readiness(examId as number),
    enabled: examId !== null,
  });
export const useReadinessOverview = () =>
  useQuery({ queryKey: queryKeys.readinessOverview, queryFn: examApi.readinessOverview });
export const useToday = () => useQuery({ queryKey: queryKeys.today, queryFn: plannerApi.today });
export const useCalendar = (from: string, to: string) =>
  useQuery({ queryKey: queryKeys.calendar(from, to), queryFn: () => plannerApi.calendar(from, to) });
export const usePriorities = () => useQuery({ queryKey: queryKeys.priorities, queryFn: plannerApi.priorities });
export const useAssessments = () => useQuery({ queryKey: queryKeys.assessments, queryFn: () => assessmentApi.list() });
export const useWeakTopics = () => useQuery({ queryKey: queryKeys.weakTopics, queryFn: performanceApi.weakTopics });
export const usePerformanceSummary = () =>
  useQuery({ queryKey: queryKeys.performanceSummary, queryFn: performanceApi.summary });
export const useTopicHistory = (topicId: number | null) =>
  useQuery({
    queryKey: queryKeys.topicHistory(topicId ?? -1),
    queryFn: () => performanceApi.topicHistory(topicId as number),
    enabled: topicId !== null,
  });
export const useRevisionsDue = () => useQuery({ queryKey: queryKeys.revisionsDue, queryFn: revisionApi.due });
export const useRevisionHistory = () =>
  useQuery({ queryKey: queryKeys.revisionHistory, queryFn: () => revisionApi.history(undefined, 30) });
export const useFocusActive = () =>
  useQuery({ queryKey: queryKeys.focusActive, queryFn: async () => (await focusApi.active()) ?? null });
export const useFocusStats = () => useQuery({ queryKey: queryKeys.focusStats, queryFn: focusApi.stats });
export const useFocusHistory = () => useQuery({ queryKey: queryKeys.focusHistory, queryFn: () => focusApi.history(14) });
export const useOverview = () => useQuery({ queryKey: queryKeys.overview, queryFn: analyticsApi.overview });
export const useAnalyticsWeekly = (date: string) =>
  useQuery({ queryKey: queryKeys.analyticsWeekly(date), queryFn: () => analyticsApi.weekly(date) });
export const useAnalyticsSubjects = () =>
  useQuery({ queryKey: queryKeys.analyticsSubjects, queryFn: analyticsApi.subjects });
export const useProductivity = (days: number) =>
  useQuery({ queryKey: queryKeys.productivity(days), queryFn: () => analyticsApi.productivity(days) });
export const useNotifications = (enabled = true) =>
  useQuery({
    queryKey: queryKeys.notifications,
    queryFn: () => notificationApi.list(false, 20),
    enabled,
    refetchInterval: 60_000,
  });
export const useAvailability = () => useQuery({ queryKey: queryKeys.availability, queryFn: availabilityApi.get });

/**
 * Most actions influence several views at once (completing a session changes the dashboard,
 * the planner, analytics, revision and readiness). Invalidating everything after a successful
 * mutation keeps every page consistent; the data set of one student is small, so this is cheap.
 */
export function useRefreshAll() {
  const queryClient = useQueryClient();
  return useCallback(() => queryClient.invalidateQueries(), [queryClient]);
}
