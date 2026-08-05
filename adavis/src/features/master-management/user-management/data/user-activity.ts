export interface UserActivityRow {
  id?: string;
  date: string;
  module: string;
  activity: string;
  description: string;
}

export const userActivityRows: UserActivityRow[] = Array.from(
  { length: 7 },
  () => ({
    date: "26/05",
    module: "IIOT",
    activity: "Analytics",
    description: "Viewed Equipment Status",
  }),
);
