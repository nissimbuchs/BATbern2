/**
 * SessionCards Component (Story 4.1.4)
 * Displays sessions in grid/list view with filtering capabilities
 * Hover to expand session description
 *
 * Structural sessions (moderation, break, lunch) are excluded from this view.
 * Materials are shown when showMaterials=true (EVENT_COMPLETED / ARCHIVE phases).
 */

import { useState, useMemo } from 'react';
import { Button } from '@/components/public/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Badge } from '@/components/public/ui/badge';
import {
  LayoutGrid,
  List,
  Clock,
  MapPin,
  Users,
  FileText,
  Video,
  Presentation,
  Download,
} from 'lucide-react';
import type { SessionUI, SessionMaterial } from '@/types/event.types';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { SpeakerDisplay } from './SpeakerDisplay';
import { eventApiClient } from '@/services/eventApiClient';

const STRUCTURAL_TYPES = new Set(['moderation', 'break', 'lunch']);

const isStructuralSession = (session: SessionUI) => STRUCTURAL_TYPES.has(session.sessionType ?? '');

interface Topic {
  id: string;
  name: string;
  color?: string;
}

interface SessionCardsProps {
  sessions: SessionUI[];
  topics?: Topic[];
  /** Show session materials download links (true in POST_EVENT / ARCHIVE phases) */
  showMaterials?: boolean;
  /** Required when showMaterials=true — used for the download presigned URL API */
  eventCode?: string;
}

export const SessionCards = ({
  sessions,
  topics = [],
  showMaterials = false,
  eventCode,
}: SessionCardsProps) => {
  const { t } = useTranslation('events');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedTopics, setSelectedTopics] = useState<string[]>([]);
  const [downloadingMaterials, setDownloadingMaterials] = useState<Set<string>>(new Set());

  // Exclude structural sessions (moderation, break, lunch)
  const contentSessions = useMemo(
    () => sessions.filter((s) => !isStructuralSession(s)),
    [sessions]
  );

  // Filter by selected topics
  const filteredSessions = useMemo(() => {
    if (selectedTopics.length === 0) return contentSessions;
    // Note: Session type doesn't have topicIds in current schema,
    // so filtering is disabled until topics are fully integrated
    return contentSessions;
  }, [contentSessions, selectedTopics]);

  const toggleTopic = (topicId: string) => {
    setSelectedTopics((prev) =>
      prev.includes(topicId) ? prev.filter((id) => id !== topicId) : [...prev, topicId]
    );
  };

  const formatSessionTime = (
    startTime: string | null | undefined,
    endTime: string | null | undefined
  ) => {
    if (!startTime || !endTime) return '';
    try {
      return `${format(new Date(startTime), 'HH:mm')} - ${format(new Date(endTime), 'HH:mm')}`;
    } catch {
      return '';
    }
  };

  // Materials helpers (mirrors EventProgram)
  const getMaterialTypeIcon = (type: string) => {
    switch (type) {
      case 'PRESENTATION':
        return <Presentation className="h-4 w-4" />;
      case 'VIDEO':
        return <Video className="h-4 w-4" />;
      default:
        return <FileText className="h-4 w-4" />;
    }
  };

  const getMaterialTypeLabel = (type: string): string => {
    switch (type) {
      case 'PRESENTATION':
        return t('public.program.materialTypes.presentation', 'Presentation');
      case 'DOCUMENT':
        return t('public.program.materialTypes.document', 'Document');
      case 'VIDEO':
        return t('public.program.materialTypes.video', 'Video');
      default:
        return t('public.program.materialTypes.other', 'Other');
    }
  };

  const formatFileSize = (bytes: number): string => `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

  const groupMaterialsByType = (materials: SessionMaterial[] | undefined) => {
    if (!materials || materials.length === 0) return {} as Record<string, SessionMaterial[]>;
    return materials.reduce(
      (acc, m) => {
        const type = m.materialType || 'OTHER';
        acc[type] = acc[type] ? [...acc[type], m] : [m];
        return acc;
      },
      {} as Record<string, SessionMaterial[]>
    );
  };

  const handleMaterialDownload = async (sessionSlug: string, materialId: string) => {
    if (!eventCode) return;
    try {
      setDownloadingMaterials((prev) => new Set(prev).add(materialId));
      const downloadUrl = await eventApiClient.getMaterialDownloadUrl(
        eventCode,
        sessionSlug,
        materialId
      );
      window.open(downloadUrl, '_blank');
    } catch (error) {
      console.error('Failed to download material:', error);
      alert(t('errors.materialDownloadFailed', 'Failed to download material. Please try again.'));
    } finally {
      setDownloadingMaterials((prev) => {
        const next = new Set(prev);
        next.delete(materialId);
        return next;
      });
    }
  };

  if (contentSessions.length === 0) {
    return null;
  }

  return (
    <div className="py-12">
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-3xl font-light text-zinc-100">{t('common:labels.sessions')}</h2>
        <div className="flex gap-2">
          <Button
            variant={viewMode === 'grid' ? 'default' : 'outline'}
            size="icon"
            onClick={() => setViewMode('grid')}
            aria-label={t('common:labels.gridView')}
            className="border-zinc-800"
          >
            <LayoutGrid className="h-4 w-4" />
          </Button>
          <Button
            variant={viewMode === 'list' ? 'default' : 'outline'}
            size="icon"
            onClick={() => setViewMode('list')}
            aria-label={t('common:labels.listView')}
            className="border-zinc-800"
          >
            <List className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Topic filters */}
      {topics.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-6">
          {topics.map((topic) => (
            <Badge
              key={topic.id}
              className={`cursor-pointer transition-colors ${
                selectedTopics.includes(topic.id)
                  ? 'bg-blue-400 text-zinc-950 hover:bg-blue-500'
                  : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
              }`}
              onClick={() => toggleTopic(topic.id)}
            >
              {topic.name}
            </Badge>
          ))}
        </div>
      )}

      {/* Sessions grid/list */}
      <div className={viewMode === 'grid' ? 'grid grid-cols-1 md:grid-cols-2 gap-6' : 'space-y-4'}>
        {filteredSessions.map((session) => (
          <Card
            key={session.sessionSlug}
            className="group bg-zinc-900/50 border-zinc-800 hover:border-zinc-700 transition-colors"
          >
            <CardHeader>
              <div className="flex items-start justify-between gap-4">
                <CardTitle className="font-light text-xl text-zinc-100">{session.title}</CardTitle>
                <Badge
                  variant="outline"
                  className="bg-blue-400/10 text-blue-400 border-blue-400/20"
                >
                  {session.sessionType}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {session.description && (
                <p className="text-sm text-zinc-400">{session.description}</p>
              )}

              <div className="flex flex-wrap gap-4 text-sm text-zinc-400">
                {session.startTime && session.endTime && (
                  <span className="flex items-center gap-1.5">
                    <Clock className="h-4 w-4" />
                    {formatSessionTime(session.startTime, session.endTime)}
                  </span>
                )}
                {session.room && (
                  <span className="flex items-center gap-1.5">
                    <MapPin className="h-4 w-4" />
                    {session.room}
                  </span>
                )}
                {session.capacity && (
                  <span className="flex items-center gap-1.5">
                    <Users className="h-4 w-4" />
                    {t('public.sessions.seats', { count: session.capacity })}
                  </span>
                )}
              </div>

              <div className="pt-2 border-t border-zinc-800">
                {session.speakers && session.speakers.length > 0 ? (
                  <div>
                    <p className="text-xs text-zinc-500 mb-2">{t('common:role.speaker')}:</p>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                      {session.speakers.map((speaker) => (
                        <SpeakerDisplay
                          key={speaker.username}
                          speaker={speaker}
                          size="small"
                          showProfilePicture={true}
                        />
                      ))}
                    </div>
                  </div>
                ) : (
                  <p className="text-sm text-zinc-400">{t('public.speakers.speakerTBA')}</p>
                )}
              </div>

              {/* Materials — shown in POST_EVENT / ARCHIVE phases */}
              {showMaterials && session.materials && session.materials.length > 0 && (
                <div className="pt-2 border-t border-zinc-800">
                  <p className="text-xs text-zinc-500 mb-3">
                    {t('public.program.materials', 'Materials')}:
                  </p>
                  <div className="space-y-4">
                    {Object.entries(groupMaterialsByType(session.materials)).map(
                      ([type, materials]) => (
                        <div key={type}>
                          <p className="text-xs font-medium text-zinc-400 mb-2 capitalize">
                            {getMaterialTypeLabel(type)}
                          </p>
                          <div className="space-y-2">
                            {materials.map((material) => {
                              const isDownloading = downloadingMaterials.has(material.id);
                              return (
                                <button
                                  key={material.id}
                                  onClick={() =>
                                    handleMaterialDownload(session.sessionSlug, material.id)
                                  }
                                  disabled={isDownloading}
                                  aria-label={t('materials.downloadAriaLabel', {
                                    fileName: material.fileName,
                                  })}
                                  className="flex items-center gap-2 p-2 rounded bg-zinc-800/50 hover:bg-zinc-800 transition-colors text-sm text-zinc-300 hover:text-blue-400 no-underline w-full text-left disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                  {getMaterialTypeIcon(material.materialType)}
                                  <span className="flex-1">{material.fileName}</span>
                                  <span className="text-xs text-zinc-500">
                                    {formatFileSize(material.fileSize)}
                                  </span>
                                  {isDownloading ? (
                                    <span className="h-4 w-4 animate-spin">⏳</span>
                                  ) : (
                                    <Download className="h-4 w-4" />
                                  )}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      )
                    )}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {filteredSessions.length === 0 && selectedTopics.length > 0 && (
        <div className="text-center py-12">
          <p className="text-zinc-400">{t('public.sessions.noSessions')}</p>
          <Button
            variant="outline"
            onClick={() => setSelectedTopics([])}
            className="mt-4 border-zinc-800"
          >
            {t('public.sessions.clearFilters')}
          </Button>
        </div>
      )}
    </div>
  );
};
