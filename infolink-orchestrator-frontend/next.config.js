/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      {
        source: '/collector-api/:path*',
        destination: 'http://localhost:8084/api/:path*',
      },
      {
        source: '/provider-api/:path*',
        destination: 'http://localhost:8095/api/:path*',
      },
      {
        source: '/auth/:path*',
        destination: 'http://localhost:9096/api/auth/:path*',
      },
      {
        source: '/api/:path*',
        destination: 'http://localhost:8080/api/:path*',
      },
    ];
  },
};

module.exports = nextConfig;
