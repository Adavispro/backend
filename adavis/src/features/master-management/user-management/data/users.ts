export interface UserRow {
  id: string;
  name: string;
  email: string;
  department: string;
  plant: string;
  status: "Active" | "Blocked";
}

export const users: UserRow[] = Array.from({ length: 7 }, (_, index) => {
  const isFirstUser = index === 0;

  return {
    id: `ID-${String(index + 9).padStart(3, "0")}`,
    name: isFirstUser ? "Priya Sharma" : "Pawan Singh",
    email: isFirstUser ? "priya@adavis.com" : "pawansingh@adavis.com",
    department: "Quality Analysis",
    plant: "Plant 01",
    status: "Active",
  };
});

export function getUserById(userId: string) {
  return users.find((user) => user.id.toLowerCase() === userId.toLowerCase());
}
