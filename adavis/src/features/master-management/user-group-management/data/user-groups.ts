export interface UserGroupRow {
  id: string;
  name: string;
  role: string;
  users: string[];
  created: string;
}

export const userGroups: UserGroupRow[] = [
  {
    id: "ID-009",
    name: "ITQA Team",
    role: "ITQA Head",
    users: ["Shreya Sharma", "Shreya Sharma", "+2"],
    created: "15-06-2026",
  },
  {
    id: "ID-023",
    name: "Quality Assurance Team",
    role: "ITQA Head",
    users: ["Shreya Sharma", "Shreya Sharma", "+2"],
    created: "15-06-2026",
  },
  ...Array.from({ length: 5 }, (_, index) => ({
    id: `ID-${String(index + 29).padStart(3, "0")}`,
    name: index === 4 ? "Quality Control" : "ITQA Team",
    role: "ITQA Head",
    users: ["Shreya Sharma", "Shreya Sharma", "+2"],
    created: "15-06-2026",
  })),
];
