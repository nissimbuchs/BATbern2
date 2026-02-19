type Speed = 'slow' | 'normal' | 'fast';

interface BATbernLoaderProps {
  size?: number;
  className?: string;
  speed?: Speed;
  'data-testid'?: string;
}

const SPEED_DURATIONS: Record<Speed, [string, string]> = {
  slow: ['1.6s', '3.6s'],
  normal: ['1.0s', '2.4s'],
  fast: ['0.6s', '1.4s'],
};

export function BATbernLoader({
  size = 40,
  className,
  speed = 'normal',
  'data-testid': testId,
}: BATbernLoaderProps) {
  const [dur1, dur2] = SPEED_DURATIONS[speed];

  return (
    <svg
      viewBox="0 -1 100 100"
      width={size}
      height={size}
      className={className}
      aria-label="Loading…"
      role="progressbar"
      data-testid={testId}
    >
      <style>{`
        @media (prefers-reduced-motion: no-preference) {
          .bat-arrow { transform-box: fill-box; }
          .bat-arrow-1 { transform-origin: 50% 87%; animation: bat-spin ${dur1} linear infinite; }
          .bat-arrow-2 { transform-origin: 50% 16%; animation: bat-spin ${dur2} linear infinite; }
          @keyframes bat-spin { to { transform: rotate(360deg); } }
        }
      `}</style>
      <g className="bat-arrow bat-arrow-1" fill="#3498DB">
        <path d="M35.822,21.061c8.877,0.261,16.278,3.112,22.344,9.105c1.02,1.007,1.862,1.383,3.196,0.678  c1.135-0.6,2.4-0.948,3.584-1.46c1.17-0.506,1.687-0.421,1.453,1.086c-0.744,4.796-1.39,9.607-2.081,14.411  c-0.306,2.128-0.647,4.251-0.936,6.381c-0.143,1.055-0.554,1.309-1.425,0.62c-5.598-4.425-11.193-8.855-16.804-13.262  c-1.002-0.787-0.533-1.142,0.32-1.479c0.972-0.384,1.941-0.774,2.907-1.172c0.489-0.202,1.214-0.249,1.232-0.898  c0.014-0.504-0.622-0.706-1.017-0.981c-7.132-4.97-17.108-5.073-24.534-0.159c-6.465,4.279-9.702,10.438-10.144,18.109  c-0.18,3.131-1.942,5.125-4.643,5.087c-2.693-0.038-4.588-2.316-4.527-5.442c0.299-15.337,12.257-28.445,27.624-30.27  C33.651,21.262,34.936,21.151,35.822,21.061z" />
      </g>
      <g className="bat-arrow bat-arrow-2" fill="#1a6fa8">
        <path d="M63.149,76.87c-7.916-0.206-15.29-3.125-21.373-9.075c-1.033-1.01-1.879-1.349-3.197-0.648  c-1.079,0.573-2.291,0.888-3.415,1.384c-1.282,0.565-1.851,0.323-1.622-1.184c0.665-4.373,1.302-8.749,1.945-13.125  c0.33-2.248,0.654-4.498,0.97-6.748c0.296-2.105,0.518-2.219,2.137-0.944c5.268,4.146,10.511,8.324,15.794,12.452  c1.139,0.89,1.436,1.475-0.233,1.994c-0.935,0.291-1.812,0.768-2.741,1.083c-1.481,0.503-1.182,1.077-0.141,1.764  c5.296,3.493,11.052,4.59,17.262,3.319c9.38-1.92,17.277-10.642,17.434-20.875c0.05-3.239,1.767-5.249,4.389-5.391  c2.683-0.145,4.711,1.851,4.785,4.709c0.337,13.023-8.736,25.494-21.634,29.705C70.331,76.326,67.061,76.839,63.149,76.87z" />
      </g>
    </svg>
  );
}
