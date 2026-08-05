import type { z } from "zod";
import type { Area, Block, Plant, Room } from "../../shared/schemas";
import type {
  areaRequestSchema,
  blockRequestSchema,
  plantRequestSchema,
  roomRequestSchema,
  topologyKindSchema,
} from "../schemas";

export type { Area, Block, Plant, Room };
export type TopologyKind = z.infer<typeof topologyKindSchema>;
export type PlantRequest = z.infer<typeof plantRequestSchema>;
export type BlockRequest = z.infer<typeof blockRequestSchema>;
export type AreaRequest = z.infer<typeof areaRequestSchema>;
export type RoomRequest = z.infer<typeof roomRequestSchema>;
export type TopologyRecord = Plant | Block | Area | Room;
export type TopologyRequest = PlantRequest | BlockRequest | AreaRequest | RoomRequest;
