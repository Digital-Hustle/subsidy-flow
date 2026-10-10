export const routes = {
  home: "/",
  specialist: "/specialist",
  login: "/specialist/login",
  packages: "/specialist/packages",
  pkg: (id: string) => `/specialist/packages/${id}`,
  stats: "/specialist/stats",
  expiring: "/specialist/expiring",
  assistant: "/specialist/assistant",
  programs: "/specialist/programs",
  staff: "/specialist/staff",
  settings: "/specialist/settings",
  org: (inn: string) => `/specialist/organizations/${inn}`,
} as const;
