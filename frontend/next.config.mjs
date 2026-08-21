/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,

  // Vercel's builder runs the static-generation pass with a single worker. In
  // that configuration Next 16.3 can finish the build without ever writing the
  // file-trace manifests (.next/*.nft.json), which Vercel's onBuildComplete
  // hook then fails to open with ENOENT. Tracing from the frontend package
  // root keeps the pass anchored to a directory that exists in the build
  // container, so the manifests are always emitted.
  // This is a comment.
  outputFileTracingRoot: import.meta.dirname,

  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
        ],
      },
    ];
  },
};

export default nextConfig;
