import { z } from "zod";
import { areaSchema, blockSchema, plantSchema, roomSchema } from "../../shared/schemas";

export const topologyKindSchema = z.enum(["plants", "blocks", "areas", "rooms"]);
export const plantsSchema = z.array(plantSchema);
export const blocksSchema = z.array(blockSchema);
export const areasSchema = z.array(areaSchema);
export const roomsSchema = z.array(roomSchema);

const common = {
  tenantId: z.string().trim().min(1, "Tenant is required."),
  isActive: z.boolean().default(true),
};

export const plantRequestSchema = z.object({
  ...common,
  plantCode: z.string().trim().min(1, "Plant code is required."),
  plantName: z.string().trim().min(1, "Plant name is required."),
  type: z.string().trim().min(1, "Plant type is required."),
  timezone: z.string().trim().min(1, "Timezone is required.").default("Asia/Kolkata"),
});

export const blockRequestSchema = z.object({
  ...common,
  plantId: z.string().trim().min(1, "Plant is required."),
  blockCode: z.string().trim().min(1, "Block code is required."),
  blockName: z.string().trim().min(1, "Block name is required."),
  displayOrder: z.coerce.number().int().nonnegative(),
});

export const areaRequestSchema = z.object({
  ...common,
  plantId: z.string().trim().min(1, "Plant is required."),
  blockId: z.string().trim().min(1, "Block is required."),
  areaCode: z.string().trim().min(1, "Area code is required."),
  areaName: z.string().trim().min(1, "Area name is required."),
  displayOrder: z.coerce.number().int().nonnegative(),
});

export const roomRequestSchema = z.object({
  ...common,
  plantId: z.string().trim().min(1, "Plant is required."),
  areaId: z.string().trim().min(1, "Area is required."),
  roomCode: z.string().trim().min(1, "Room code is required."),
  roomName: z.string().trim().min(1, "Room name is required."),
  classification: z.string().trim().min(1, "Classification is required."),
});
