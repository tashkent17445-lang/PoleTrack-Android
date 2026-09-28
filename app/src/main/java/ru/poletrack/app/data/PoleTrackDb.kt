package ru.poletrack.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PoleTrackDb(context: Context) : SQLiteOpenHelper(context, "poletrack.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE routes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                finished_at INTEGER,
                spool_start_m INTEGER NOT NULL,
                sag_percent REAL NOT NULL,
                start_reserve_m REAL NOT NULL,
                end_reserve_m REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE route_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                route_id INTEGER NOT NULL,
                seq INTEGER NOT NULL,
                type TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                created_at INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                tag TEXT NOT NULL DEFAULT '',
                extra_m REAL NOT NULL DEFAULT 0,
                FOREIGN KEY(route_id) REFERENCES routes(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_route_points_route ON route_points(route_id, seq)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    fun createRoute(
        name: String,
        spoolStartMeters: Int,
        sagPercent: Double,
        startReserveMeters: Double,
        endReserveMeters: Double
    ): Route {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name)
            put("created_at", now)
            put("spool_start_m", spoolStartMeters)
            put("sag_percent", sagPercent)
            put("start_reserve_m", startReserveMeters)
            put("end_reserve_m", endReserveMeters)
        }
        val id = writableDatabase.insertOrThrow("routes", null, values)
        return Route(id, name, now, null, spoolStartMeters, sagPercent, startReserveMeters, endReserveMeters)
    }

    fun finishRoute(routeId: Long) {
        val values = ContentValues().apply { put("finished_at", System.currentTimeMillis()) }
        writableDatabase.update("routes", values, "id=?", arrayOf(routeId.toString()))
    }

    fun addPoint(
        routeId: Long,
        type: PointType,
        latitude: Double,
        longitude: Double,
        note: String = "",
        tag: String = "",
        extraMeters: Double = 0.0
    ): RoutePoint {
        val sequence = nextSequence(routeId)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("route_id", routeId)
            put("seq", sequence)
            put("type", type.name)
            put("latitude", latitude)
            put("longitude", longitude)
            put("created_at", now)
            put("note", note)
            put("tag", tag)
            put("extra_m", extraMeters)
        }
        val id = writableDatabase.insertOrThrow("route_points", null, values)
        return RoutePoint(id, routeId, sequence, type, latitude, longitude, now, note, tag, extraMeters)
    }

    fun updatePoint(pointId: Long, note: String, tag: String, extraMeters: Double) {
        val values = ContentValues().apply {
            put("note", note)
            put("tag", tag)
            put("extra_m", extraMeters)
        }
        writableDatabase.update("route_points", values, "id=?", arrayOf(pointId.toString()))
    }

    fun getActiveRoute(): Route? = queryRoutes("finished_at IS NULL", null).firstOrNull()

    fun getRoutes(): List<Route> = queryRoutes(null, null)

    fun getRoute(routeId: Long): Route? = queryRoutes("id=?", arrayOf(routeId.toString())).firstOrNull()

    fun getPoints(routeId: Long): List<RoutePoint> {
        val out = mutableListOf<RoutePoint>()
        readableDatabase.query(
            "route_points",
            null,
            "route_id=?",
            arrayOf(routeId.toString()),
            null,
            null,
            "seq ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out += RoutePoint(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    routeId = c.getLong(c.getColumnIndexOrThrow("route_id")),
                    sequence = c.getInt(c.getColumnIndexOrThrow("seq")),
                    type = PointType.valueOf(c.getString(c.getColumnIndexOrThrow("type"))),
                    latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                    longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    note = c.getString(c.getColumnIndexOrThrow("note")),
                    tag = c.getString(c.getColumnIndexOrThrow("tag")),
                    extraMeters = c.getDouble(c.getColumnIndexOrThrow("extra_m"))
                )
            }
        }
        return out
    }

    private fun nextSequence(routeId: Long): Int {
        readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(seq), -1) + 1 FROM route_points WHERE route_id=?",
            arrayOf(routeId.toString())
        ).use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    private fun queryRoutes(selection: String?, args: Array<String>?): List<Route> {
        val out = mutableListOf<Route>()
        readableDatabase.query(
            "routes",
            null,
            selection,
            args,
            null,
            null,
            "created_at DESC"
        ).use { c ->
            while (c.moveToNext()) {
                val finishedIndex = c.getColumnIndexOrThrow("finished_at")
                out += Route(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    finishedAt = if (c.isNull(finishedIndex)) null else c.getLong(finishedIndex),
                    spoolStartMeters = c.getInt(c.getColumnIndexOrThrow("spool_start_m")),
                    sagPercent = c.getDouble(c.getColumnIndexOrThrow("sag_percent")),
                    startReserveMeters = c.getDouble(c.getColumnIndexOrThrow("start_reserve_m")),
                    endReserveMeters = c.getDouble(c.getColumnIndexOrThrow("end_reserve_m"))
                )
            }
        }
        return out
    }
}
