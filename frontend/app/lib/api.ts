export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
export const API_V1_URL = `${API_BASE_URL}/api/v1`;

export const API_ENDPOINTS = {
  csrf: `${API_V1_URL}/csrf`,
  auth: {
    login: `${API_V1_URL}/auth/login`,
    logout: `${API_V1_URL}/auth/logout`,
  },
  members: {
    me: `${API_V1_URL}/members/me`,
    signup: `${API_V1_URL}/members`,
  },
  images: {
    upload: `${API_V1_URL}/images`,
  },
  master: {
    resorts: `${API_V1_URL}/master/resorts`,
    ridingStyles: `${API_V1_URL}/master/riding-styles`,
  },
  posts: {
    list: `${API_V1_URL}/posts`,
    create: `${API_V1_URL}/posts`,
    detail: (publicId: string) => `${API_V1_URL}/posts/${publicId}`,
    update: (publicId: string) => `${API_V1_URL}/posts/${publicId}`,
    delete: (publicId: string) => `${API_V1_URL}/posts/${publicId}`,
    reaction: (publicId: string, type: "LIKE" | "DISLIKE") =>
      `${API_V1_URL}/posts/${publicId}/reaction?type=${type}`,
    comments: (publicId: string, cursor?: string | null, size = 20) =>
      `${API_V1_URL}/posts/${publicId}/comments?${
        cursor != null ? `cursor=${cursor}&size=${size}` : `size=${size}`
      }`,
  },
  comments: {
    replies: (commentId: string, cursor?: string | null, size = 20) =>
      `${API_V1_URL}/comments/${commentId}/replies?${
        cursor != null ? `cursor=${cursor}&size=${size}` : `size=${size}`
      }`,
    delete: (commentId: string) => `${API_V1_URL}/comments/${commentId}`,
  },
  chat: {
    recent: `${API_V1_URL}/chat/recent`,
  },
} as const;
