/** @type {import('next').NextConfig} */
const nextConfig = {
  /**
   * 브라우저의 /api 요청을 백엔드(8080)로 그대로 넘긴다.
   * 프론트가 8080을 직접 부르면 출처가 달라 CORS 설정을 백엔드에 따로 넣어야 하는데,
   * 여기서 넘기면 브라우저 입장에서는 같은 출처라 그럴 필요가 없다.
   */
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://localhost:8080/api/:path*',
      },
    ];
  },

  experimental: {
    /**
     * 위 프록시의 응답 대기 시간(ms).
     * 기본값이 30초인데 39페이지 분류에 OCR과 LLM 호출까지 1~2분이 걸려 그대로 두면 끊긴다.
     */
    proxyTimeout: 300000,
  },
};

export default nextConfig;
