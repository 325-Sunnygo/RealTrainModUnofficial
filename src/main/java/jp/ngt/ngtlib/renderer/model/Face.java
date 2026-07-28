package jp.ngt.ngtlib.renderer.model;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.Face のスクリプト互換移植。
 * vertices は面の頂点 (四角形は4点)。uvs は頂点順の {u,v}。
 */
public class Face {
    /** ミラー軸ごとの符号反転パターン (本家 MIRROR_PATTERN)。 */
    private static final float[][] MIRROR_PATTERN = {
            {-1.0F, 1.0F, 1.0F},
            {1.0F, -1.0F, 1.0F},
            {1.0F, 1.0F, -1.0F}};

    public final Vertex[] vertices;
    /** 頂点順の u,v (長さ vertices.length*2)。UV 無しは null。 */
    public final float[] uvs;
    public final int materialId;
    public Vertex faceNormal;
    /** スムージング用の頂点法線。calcVertexNormals で作られる。 */
    public Vertex[] vertexNormals;
    /** textureCoordinates ビューのキャッシュ (初回アクセス時に uvs から作る)。 */
    private TextureCoordinate[] texCoordView;

    public Face(Vertex[] vertices, float[] uvs, int materialId) {
        this.vertices = vertices;
        this.uvs = uvs;
        this.materialId = materialId;
    }

    /** 本家 Face(size, material): 空の面を作る (addVertex で埋める)。 */
    public Face(int size, int material) {
        this(new Vertex[size], new float[size * 2], material);
    }

    /**
     * 本家 textureCoordinates。
     * スクリプトが face.textureCoordinates[i].getU と書けるようビューを返す。
     */
    public TextureCoordinate[] getTextureCoordinates() {
        if (this.texCoordView == null) {
            int n = this.vertices.length;
            TextureCoordinate[] out = new TextureCoordinate[n];
            for (int i = 0; i < n; i++) {
                float u = this.uvs != null && i * 2 + 1 < this.uvs.length ? this.uvs[i * 2] : 0.0F;
                float v = this.uvs != null && i * 2 + 1 < this.uvs.length ? this.uvs[i * 2 + 1] : 0.0F;
                out[i] = new TextureCoordinate(u, v);
            }
            this.texCoordView = out;
        }
        return this.texCoordView;
    }

    public Vertex[] getVertexNormals() {
        return this.vertexNormals;
    }

    /** 本家 addVertex: 指定位置に頂点と UV を入れる。 */
    public void addVertex(int index, Vertex vertex, TextureCoordinate coord) {
        this.vertices[index] = vertex;
        if (this.uvs != null && coord != null && index * 2 + 1 < this.uvs.length) {
            this.uvs[index * 2] = coord.getU();
            this.uvs[index * 2 + 1] = coord.getV();
        }
        this.texCoordView = null;
    }

    /**
     * 本家 calcVertexNormals: 隣接面の法線をスムージング角以内なら足し込んで頂点法線を作る。
     * map は「頂点 → その頂点を共有する面」。
     */
    public void calcVertexNormals(java.util.Map<Vertex, java.util.List<Face>> map, float angleCos, VecAccuracy accuracy) {
        if (this.faceNormal == null) {
            this.calculateFaceNormal(accuracy);
        }
        int n = this.vertices.length;
        this.vertexNormals = new Vertex[n];
        for (int i = 0; i < n; i++) {
            Vertex base = this.faceNormal;
            float nx = base.getX();
            float ny = base.getY();
            float nz = base.getZ();
            java.util.List<Face> shared = map == null ? null : map.get(this.vertices[i]);
            if (shared != null) {
                for (Face other : shared) {
                    if (other == this || other.faceNormal == null) {
                        continue;
                    }
                    // 法線同士の角がスムージング角以内なら足す (cos 比較)
                    float dot = base.getX() * other.faceNormal.getX()
                            + base.getY() * other.faceNormal.getY()
                            + base.getZ() * other.faceNormal.getZ();
                    if (dot >= angleCos) {
                        nx += other.faceNormal.getX();
                        ny += other.faceNormal.getY();
                        nz += other.faceNormal.getZ();
                    }
                }
            }
            Vertex v = new Vertex(nx, ny, nz);
            v.normalize();
            this.vertexNormals[i] = v;
        }
    }

    /** 本家 addFaceForRender: tessellator へ自分の頂点を積む。 */
    public void addFaceForRender(Object tessellator, boolean smoothing) {
        jp.ngt.ngtlib.renderer.NGTRenderHelper.addFace(this, tessellator, smoothing);
    }

    /**
     * 本家 getMirror: 指定軸で反転した面を返す。type は 0=X, 1=Y, 2=Z。
     * 反転で頂点の巻き順が裏返るので、index も反転させる。
     */
    public Face getMirror(int type, java.util.Map<Vertex, Vertex> mirrorVertex, VecAccuracy accuracy) {
        if (type < 0 || type > 2) {
            return this.copy();
        }
        int size = this.vertices.length;
        Face face = new Face(size, this.materialId);
        TextureCoordinate[] coords = this.getTextureCoordinates();
        for (int i = 0; i < size; i++) {
            Vertex src = this.vertices[i];
            int index = i > 0 ? size - i : 0;
            Vertex mirrored;
            if (mirrorVertex != null && mirrorVertex.containsKey(src)) {
                // 元で共有していた頂点は反転後も共有させる
                mirrored = mirrorVertex.get(src);
            } else {
                float x = src.getX() * MIRROR_PATTERN[type][0];
                float y = src.getY() * MIRROR_PATTERN[type][1];
                float z = src.getZ() * MIRROR_PATTERN[type][2];
                boolean onPlane = (type == 0 && x == 0.0F) || (type == 1 && y == 0.0F) || (type == 2 && z == 0.0F);
                mirrored = onPlane ? src : new Vertex(x, y, z);
                if (mirrorVertex != null) {
                    mirrorVertex.put(src, mirrored);
                }
            }
            face.addVertex(index, mirrored, coords[i]);
        }
        return face;
    }

    public Face copy() {
        Face face = new Face(this.vertices.length, this.materialId);
        TextureCoordinate[] coords = this.getTextureCoordinates();
        for (int i = 0; i < this.vertices.length; i++) {
            face.addVertex(i, this.vertices[i].copy(), coords[i].copy());
        }
        return face;
    }

    public void calculateFaceNormal(VecAccuracy accuracy) {
        if (this.vertices.length < 3) {
            this.faceNormal = new Vertex(0.0F, 1.0F, 0.0F);
            return;
        }
        Vertex v0 = this.vertices[0];
        Vertex v1 = this.vertices[1];
        Vertex v2 = this.vertices[2];
        float ax = v1.x - v0.x, ay = v1.y - v0.y, az = v1.z - v0.z;
        float bx = v2.x - v0.x, by = v2.y - v0.y, bz = v2.z - v0.z;
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        // ★大きさで捨てない。先に最大成分で割ってから正規化する (細かい文字の三角形対策)。
        float max = Math.max(Math.abs(nx), Math.max(Math.abs(ny), Math.abs(nz)));
        if (!(max > 0.0F) || !Float.isFinite(max)) {
            this.faceNormal = new Vertex(0.0F, 1.0F, 0.0F);
            return;
        }
        nx /= max;
        ny /= max;
        nz /= max;
        float len = (float) Math.sqrt((double) nx * nx + (double) ny * ny + (double) nz * nz);
        if (!(len > 0.0F) || !Float.isFinite(len)) {
            this.faceNormal = new Vertex(0.0F, 1.0F, 0.0F);
        } else {
            this.faceNormal = new Vertex(nx / len, ny / len, nz / len);
        }
    }
}
