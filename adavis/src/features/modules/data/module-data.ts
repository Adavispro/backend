import { ROUTES } from "@/config/routes";
import type { ModuleSection } from "@/features/modules/types/module.types";

export const MODULE_SECTIONS: ModuleSection[] = [
  {
    id: "admin",
    title: "ADMIN",
    modules: [
      {
        id: "master-management",
        title: "Master Management",
        description:
          "Manage master records, configurations, users, and system-wide operational settings.",
        iconName: "UsersThree",
        iconBg: "#EAF0FB",
        iconColor: "#064FA5",
        href: ROUTES.masterManagement,
      },
      {
        id: "iiot",
        title: "IIoT",
        description:
          "Centralized repository for maintaining and governing critical business data.",
        iconName: "Database",
        iconBg: "#E6F4F1",
        iconColor: "#0D7E6A",
        href: ROUTES.iiot,
      },
      {
        id: "project-engine",
        title: "Project Engine",
        description:
          "Plan, track, and manage operational and implementation projects efficiently.",
        iconName: "Kanban",
        iconBg: "#FEF3E2",
        iconColor: "#C06A0A",
        href: ROUTES.projectEngine,
      },
    ],
  },
  {
    id: "manufacturing-operations",
    title: "MANUFACTURING OPERATIONS",
    modules: [
      {
        id: "ai-elogbook",
        title: "AI eLogbook",
        description:
          "Digitize operational logbooks and streamline real-time manufacturing entries.",
        iconName: "NotePencil",
        iconBg: "#E8F5E9",
        iconColor: "#1B7F3A",
        href: ROUTES.manufacturingElogbook,
      },
      {
        id: "ai-ebmr",
        title: "AI eBMR",
        description:
          "Manage electronic batch manufacturing records with automated workflows and approvals.",
        iconName: "ClipboardText",
        iconBg: "#E8F5E9",
        iconColor: "#1B7F3A",
        href: ROUTES.manufacturingEbmr,
      },
      {
        id: "ai-iot",
        title: "AI IoT",
        description:
          "Monitor connected devices, equipment performance, and live manufacturing operations.",
        iconName: "Cpu",
        iconBg: "#EAF0FB",
        iconColor: "#064FA5",
        href: ROUTES.iiotEquipment,
      },
      {
        id: "cleaning-validations",
        title: "Cleaning Validations",
        description:
          "Plan, execute, and document cleaning validation processes with compliance tracking.",
        iconName: "ShieldCheck",
        iconBg: "#F0EBFB",
        iconColor: "#6B3EBF",
        href: ROUTES.manufacturingCleaningValidations,
      },
    ],
  },
  {
    id: "quality-analysis",
    title: "QUALITY ANALYSIS",
    modules: [
      {
        id: "ai-qms",
        title: "AI Quality Management System",
        description:
          "Manage quality processes, deviations, CAPAs and continuous improvement activities.",
        iconName: "MagnifyingGlass",
        iconBg: "#EAF0FB",
        iconColor: "#064FA5",
        href: ROUTES.qualityQms,
      },
      {
        id: "apqr",
        title: "Annual Product Quality Review",
        description:
          "Analyze annual product performance, trends, and compliance review data.",
        iconName: "ChartBar",
        iconBg: "#FEF3E2",
        iconColor: "#C06A0A",
        href: ROUTES.qualityApqr,
      },
      {
        id: "ai-investigation",
        title: "AI Investigation",
        description:
          "Track, investigate, and resolve deviations, incidents, and quality events efficiently.",
        iconName: "Detective",
        iconBg: "#EAF0FB",
        iconColor: "#064FA5",
        href: ROUTES.qualityInvestigation,
      },
    ],
  },
  {
    id: "compliance-documentation",
    title: "COMPLIANCE AND DOCUMENTATION",
    modules: [
      {
        id: "ai-audit",
        title: "AI Audit Management System",
        description:
          "Simplify audit planning, execution, findings, and compliance monitoring.",
        iconName: "Scales",
        iconBg: "#FEF3E2",
        iconColor: "#C06A0A",
        href: ROUTES.complianceAudit,
      },
      {
        id: "ai-dms",
        title: "AI Document Management System",
        description:
          "Securely manage, organize, approve, and maintain controlled documents digitally.",
        iconName: "Files",
        iconBg: "#E6F4F1",
        iconColor: "#0D7E6A",
        href: ROUTES.complianceDms,
      },
    ],
  },
];
