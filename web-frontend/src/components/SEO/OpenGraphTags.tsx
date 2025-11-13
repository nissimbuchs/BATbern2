/**
 * OpenGraphTags Component (Story 4.1.4)
 * SEO and social sharing meta tags using react-helmet-async
 */

import { Helmet } from 'react-helmet-async';

interface OpenGraphTagsProps {
  title: string;
  description: string;
  url: string;
  image?: string;
  type?: string;
}

export const OpenGraphTags = ({
  title,
  description,
  url,
  image,
  type = 'website',
}: OpenGraphTagsProps) => {
  const siteName = 'BATbern';
  const fullTitle = `${title} | ${siteName}`;
  const defaultImage = image || 'https://cdn.batbern.ch/assets/default-event-cover.jpg';

  return (
    <Helmet>
      {/* Primary Meta Tags */}
      <title>{fullTitle}</title>
      <meta name="title" content={fullTitle} />
      <meta name="description" content={description} />

      {/* Open Graph / Facebook */}
      <meta property="og:type" content={type} />
      <meta property="og:url" content={url} />
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={description} />
      <meta property="og:image" content={defaultImage} />
      <meta property="og:site_name" content={siteName} />

      {/* Twitter */}
      <meta name="twitter:card" content="summary_large_image" />
      <meta name="twitter:url" content={url} />
      <meta name="twitter:title" content={fullTitle} />
      <meta name="twitter:description" content={description} />
      <meta name="twitter:image" content={defaultImage} />

      {/* Additional SEO */}
      <link rel="canonical" href={url} />
    </Helmet>
  );
};
