package com.jwwviewer.app.translate

/**
 * Architecture / JWW drawing terms — exact or longest-first replace
 * so generic MT does not mangling meaning (通り≠street, etc.).
 */
object CadGlossary {
    private data class Entry(val ja: String, val vi: String, val en: String, val zh: String)

    private val entries: List<Entry> = listOf(
        Entry("通り芯", "Tim trục", "Grid axis", "轴心"),
        Entry("通り心", "Tim trục", "Grid axis", "轴心"),
        Entry("平面図", "Mặt bằng", "Floor plan", "平面图"),
        Entry("立面図", "Mặt đứng", "Elevation", "立面图"),
        Entry("断面図", "Mặt cắt", "Section", "剖面图"),
        Entry("矩計図", "Chi tiết tường (cắt)", "Wall section detail", "详图（墙身）"),
        Entry("伏図", "Mặt bằng kết cấu", "Framing plan", "结构平面图"),
        Entry("配筋図", "Bản vẽ bố trí cốt thép", "Rebar layout", "配筋图"),
        Entry("構造図", "Bản vẽ kết cấu", "Structural drawing", "结构图"),
        Entry("設備図", "Bản vẽ thiết bị", "MEP drawing", "设备图"),
        Entry("電気設備", "Thiết bị điện", "Electrical equipment", "电气设备"),
        Entry("給排水", "Cấp thoát nước", "Plumbing", "给排水"),
        Entry("空調", "Điều hòa không khí", "HVAC", "空调"),
        Entry("見上図", "Mặt nhìn lên (trần)", "Reflected ceiling plan", "仰视图"),
        Entry("見下図", "Mặt nhìn xuống", "Looking-down plan", "俯视图"),
        Entry("配置図", "Sơ đồ bố trí", "Site / layout plan", "布置图"),
        Entry("案内図", "Sơ đồ hướng dẫn", "Key plan / guide map", "导览图"),
        Entry("縮尺", "Tỷ lệ", "Scale", "比例"),
        Entry("寸法", "Kích thước", "Dimension", "尺寸"),
        Entry("仕上", "Hoàn thiện", "Finish", "装修"),
        Entry("仕上げ", "Hoàn thiện", "Finish", "装修"),
        Entry("鉄筋コンクリート", "Bê tông cốt thép", "Reinforced concrete", "钢筋混凝土"),
        Entry("コンクリート", "Bê tông", "Concrete", "混凝土"),
        Entry("鉄筋", "Cốt thép", "Rebar", "钢筋"),
        Entry("鉄骨", "Kết cấu thép", "Steel frame", "钢结构"),
        Entry("基礎", "Móng", "Foundation", "基础"),
        Entry("床スラブ", "Sàn (slab)", "Floor slab", "楼板"),
        Entry("スラブ", "Sàn / bản", "Slab", "板"),
        Entry("梁", "Dầm", "Beam", "梁"),
        Entry("柱", "Cột", "Column", "柱"),
        Entry("壁", "Tường", "Wall", "墙"),
        Entry("床", "Sàn", "Floor", "地板"),
        Entry("天井", "Trần", "Ceiling", "天花"),
        Entry("屋根", "Mái", "Roof", "屋顶"),
        Entry("開口", "Lỗ mở", "Opening", "开口"),
        Entry("建具", "Cửa / cửa sổ (cửa công trình)", "Doors & windows", "门窗"),
        Entry("防火", "Chống cháy", "Fireproof", "防火"),
        Entry("避難", "Thoát hiểm", "Evacuation", "疏散"),
        Entry("既存", "Hiện trạng / hiện hữu", "Existing", "既有"),
        Entry("新設", "Mới xây / mới lắp", "New", "新建"),
        Entry("撤去", "Phá dỡ / tháo dỡ", "Remove / demolish", "拆除"),
        Entry("改修", "Cải tạo", "Renovation", "改建"),
        Entry("増築", "Xây thêm", "Extension", "扩建"),
        Entry("通り", "Trục (tim)", "Grid line / axis", "轴线"),
        Entry("芯", "Tim", "Center line", "中心线"),
        Entry("レベル", "Cao độ", "Level / elevation", "标高"),
        Entry("高さ", "Chiều cao", "Height", "高度"),
        Entry("幅", "Chiều rộng", "Width", "宽度"),
        Entry("厚さ", "Chiều dày", "Thickness", "厚度"),
        Entry("勾配", "Độ dốc", "Slope / gradient", "坡度"),
        Entry("北", "Hướng Bắc", "North", "北"),
        Entry("注記", "Chú thích", "Note", "注释"),
        Entry("特記", "Ghi chú đặc biệt", "Special note", "特记"),
        Entry("など", "v.v.", "etc.", "等"),
    ).sortedByDescending { it.ja.length }

    fun lookupExact(ja: String, target: TranslateTarget): String? {
        val key = normalizeCadText(ja).replace(" ", "").replace("　", "")
        if (key.isBlank()) return null
        val hit = entries.firstOrNull { it.ja == key } ?: return null
        return hit.pick(target)
    }

    /** Replace known terms with placeholders before MT; restore after. */
    fun protect(text: String, target: TranslateTarget): Pair<String, List<String>> {
        var work = text
        val restored = ArrayList<String>()
        for (e in entries) {
            if (!work.contains(e.ja)) continue
            val token = "⟦${restored.size}⟧"
            restored.add(e.pick(target))
            work = work.replace(e.ja, token)
        }
        return work to restored
    }

    fun restore(translated: String, parts: List<String>): String {
        var out = translated
        parts.forEachIndexed { i, v ->
            out = out.replace("⟦$i⟧", v)
            out = out.replace("[[$i]]", v)
            out = out.replace("[$i]", v)
        }
        return out
    }

    private fun Entry.pick(target: TranslateTarget): String = when (target) {
        TranslateTarget.VI -> vi
        TranslateTarget.EN -> en
        TranslateTarget.ZH -> zh
    }
}
