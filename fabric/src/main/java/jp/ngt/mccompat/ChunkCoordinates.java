package jp.ngt.mccompat;

/** 1.7.10 command sender coordinates, including MCP and SRG field names. */
public class ChunkCoordinates {
    public int posX, posY, posZ;

    public ChunkCoordinates(int x, int y, int z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    // Bean accessors let Nashorn read and write SRG aliases without duplicate mutable fields.
    public int getField_71574_a() { return this.posX; }
    public void setField_71574_a(int value) { this.posX = value; }
    public int getField_71572_b() { return this.posY; }
    public void setField_71572_b(int value) { this.posY = value; }
    public int getField_71573_c() { return this.posZ; }
    public void setField_71573_c(int value) { this.posZ = value; }

    public void set(int x, int y, int z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public float getDistanceSquared(int x, int y, int z) {
        float dx = this.posX - x, dy = this.posY - y, dz = this.posZ - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
