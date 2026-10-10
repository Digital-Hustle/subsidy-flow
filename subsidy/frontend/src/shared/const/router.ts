export const routes = {
  home: "/",
  apply: "/apply",
  applyApplication: (id: string) => `/apply/${id}`,
  operator: "/operator",
  operatorApplication: (id: string) => `/operator/applications/${id}`,
  operatorOrganization: (inn: string) => `/operator/organizations/${inn}`,
  operatorExpiring: "/operator/expiring",
  operatorMetrics: "/operator/metrics",
  operatorPrograms: "/operator/programs",
  operatorStaff: "/operator/staff",
} as const;
