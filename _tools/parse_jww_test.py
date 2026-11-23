from __future__ import annotations
import struct, zipfile, io, sys, os
from dataclasses import dataclass, field

def unwrap(data: bytes) -> bytes:
    if data[:2] == b"PK":
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            return z.read(z.namelist()[0])
    return data

class Reader:
    def __init__(self, data: bytes):
        self.d = data
        self.o = 0
        self.class_map: dict[int, str] = {}
        self.index = 1

    def left(self) -> int:
        return len(self.d) - self.o

    def u8(self) -> int:
        v = self.d[self.o]; self.o += 1; return v
    def u16(self) -> int:
        v = struct.unpack_from("<H", self.d, self.o)[0]; self.o += 2; return v
    def u32(self) -> int:
        v = struct.unpack_from("<I", self.d, self.o)[0]; self.o += 4; return v
    def f64(self) -> float:
        v = struct.unpack_from("<d", self.d, self.o)[0]; self.o += 8; return v
    def raw(self, n: int) -> bytes:
        v = self.d[self.o:self.o+n]; self.o += n; return v

    def cstring(self) -> str:
        b = self.u8()
        if b == 0: return ""
        if b != 0xFF:
            return self.raw(b).decode("cp932", "replace")
        w = self.u16()
        if w == 0xFFFE:
            cb = self.u8()
            if cb < 0xFF: n = cb
            else:
                n = self.u16()
                if n == 0xFFFF: n = self.u32()
            return self.raw(n * 2).decode("utf-16-le", "replace")
        if w == 0xFFFF:
            n = self.u32(); return self.raw(n).decode("cp932", "replace")
        return self.raw(w).decode("cp932", "replace")

    def count(self) -> int:
        w = self.u16()
        return self.u32() if w == 0xFFFF else w

    def object_tag(self) -> str:
        w = self.u16()
        if w == 0xFFFF:
            schema = self.u16()
            nlen = self.u16()
            name = self.raw(nlen).decode("ascii", "replace")
            self.class_map[self.index] = name
            self.index += 1
            self.index += 1
            return name
        if w == 0x7FFF:
            dw = self.u32()
        else:
            dw = (0x80000000 | (w & 0x7FFF)) if (w & 0x8000) else w
        if not (dw & 0x80000000):
            self.index += 1
            return ""
        cid = dw & 0x7FFFFFFF
        name = self.class_map.get(cid)
        if name is None:
            raise RuntimeError(f"missing class {cid} known={self.class_map} @{self.o}")
        self.index += 1
        return name

@dataclass
class Attr:
    group: int; pen: int; color: int; width: int; layer: int; glayer: int; flg: int

@dataclass
class Drawing:
    version: int; memo: str; scales: list; layer_states: list
    lines: list = field(default_factory=list)
    circles: list = field(default_factory=list)
    texts: list = field(default_factory=list)
    solids: list = field(default_factory=list)
    points: list = field(default_factory=list)
    blocks: list = field(default_factory=list)
    error: str = ""

def read_attr(r, ver):
    group=r.u32(); pen=r.u8(); color=r.u16()
    width=r.u16() if ver>=351 else 1
    layer=r.u16(); glayer=r.u16(); flg=r.u16()
    return Attr(group,pen,color,width,layer,glayer,flg)

def read_header(r):
    sig=r.raw(8)
    if sig!=b"JwwData.": raise ValueError(sig)
    ver=r.u32()
    memo=r.cstring(); r.u32(); r.u32()
    scales=[]; layer_states=[]
    for _ in range(16):
        r.u32(); r.u32(); scales.append(r.f64()); r.u32()
        for __ in range(16):
            layer_states.append(r.u32()); r.u32()
    for _ in range(14): r.u32()
    for _ in range(5): r.u32()
    r.u32(); r.u32()
    r.f64(); r.f64(); r.f64(); r.u32()
    r.u32(); r.f64(); r.f64(); r.f64(); r.f64(); r.f64()
    for _ in range(16):
        for __ in range(16): r.cstring()
    for _ in range(16): r.cstring()
    r.f64(); r.f64(); r.u32(); r.f64()
    if ver>=300: r.f64(); r.f64()
    r.u32(); r.f64(); r.f64(); r.f64()
    r.f64(); r.f64(); r.f64()
    if ver>=300:
        for _ in range(8):
            r.f64(); r.f64(); r.f64(); r.u32()
        r.f64(); r.f64(); r.f64(); r.u32()
        r.f64(); r.f64(); r.f64(); r.u32()
    for _ in range(10): r.f64()
    r.f64()
    for _ in range(10): r.u32(); r.u32()
    for _ in range(10): r.u32(); r.u32(); r.f64()
    for _ in range(2,10):
        for __ in range(4): r.u32()
    for _ in range(11,16):
        for __ in range(5): r.u32()
    for _ in range(16,20):
        for __ in range(4): r.u32()
    for _ in range(11): r.u32()
    r.u32(); r.u32()
    r.u32(); r.u32(); r.u32()
    r.f64(); r.f64(); r.f64(); r.f64(); r.f64()
    r.f64(); r.f64(); r.f64(); r.f64()
    r.u32(); r.u32()
    if ver>=420:
        for _ in range(257): r.u32(); r.u32()
        for _ in range(257):
            r.cstring(); r.u32(); r.u32(); r.f64()
        for _ in range(33):
            for __ in range(4): r.u32()
        for _ in range(33):
            r.cstring(); r.u32()
            for __ in range(10): r.f64()
    for _ in range(10):
        r.f64(); r.f64(); r.f64(); r.u32()
    r.f64(); r.f64(); r.f64(); r.u32(); r.u32()
    r.f64(); r.f64(); r.u32()
    r.f64(); r.f64(); r.f64(); r.f64(); r.f64(); r.f64()
    return Drawing(ver, memo, scales, layer_states)

def read_ten_extra(r, a):
    r.u32()
    if a.pen==100:
        r.u32(); r.f64(); r.f64()

def read_sunpou(r, ver, d):
    read_attr(r, ver)
    a1=read_attr(r, ver)
    d.lines.append((a1, r.f64(), r.f64(), r.f64(), r.f64()))
    a2=read_attr(r, ver)
    vals=[r.f64() for _ in range(4)]
    shu=r.u32(); sx,sy,gap,ang=r.f64(),r.f64(),r.f64(),r.f64()
    font=r.cstring(); text=r.cstring()
    d.texts.append((a2,*vals,shu,sx,sy,gap,ang,font,text))
    if ver>=420:
        r.u16()
        for _ in range(2):
            read_attr(r,ver)
            for __ in range(4): r.f64()
        for _ in range(4):
            a3=read_attr(r,ver)
            r.f64(); r.f64(); read_ten_extra(r,a3)

def consume_named(r, name, ver, d, store=True):
    if name=="CDataSen":
        a=read_attr(r,ver); vals=(a,r.f64(),r.f64(),r.f64(),r.f64())
        if store: d.lines.append(vals)
    elif name=="CDataEnko":
        a=read_attr(r,ver); vals=(a,r.f64(),r.f64(),r.f64(),r.f64(),r.f64(),r.f64(),r.f64(),r.u32())
        if store: d.circles.append(vals)
    elif name=="CDataTen":
        a=read_attr(r,ver); x,y=r.f64(),r.f64(); read_ten_extra(r,a)
        if store: d.points.append((a,x,y))
    elif name=="CDataMoji":
        a=read_attr(r,ver); vals=[r.f64() for _ in range(4)]
        shu=r.u32(); sx,sy,gap,ang=r.f64(),r.f64(),r.f64(),r.f64()
        font=r.cstring(); text=r.cstring()
        if store: d.texts.append((a,*vals,shu,sx,sy,gap,ang,font,text))
    elif name=="CDataSolid":
        a=read_attr(r,ver); pts=[r.f64() for _ in range(8)]
        if a.color==10: r.u32()
        if store: d.solids.append((a,pts))
    elif name=="CDataBlock":
        a=read_attr(r,ver); vals=(a,r.f64(),r.f64(),r.f64(),r.f64(),r.f64(),r.u32())
        if store: d.blocks.append(vals)
    elif name=="CDataSunpou":
        read_sunpou(r,ver,d if store else Drawing(ver,"",[],[]))
    else:
        raise RuntimeError("unsupported "+name)

def parse_file(path):
    r=Reader(unwrap(open(path,"rb").read()))
    d=read_header(r)
    ver=d.version
    try:
        count=r.count()
        for _ in range(count):
            name=r.object_tag()
            if not name: continue
            consume_named(r,name,ver,d,True)
        if r.left()>=2:
            bcount=r.count()
            for _ in range(bcount):
                name=r.object_tag()
                if name!="CDataList": break
                read_attr(r,ver)
                r.u32(); r.u32()
                # CTime as DWORD in LibreCAD jwwdoc.h
                r.u32()
                r.cstring()
                mcount=r.count()
                for __ in range(mcount):
                    mn=r.object_tag()
                    if not mn: continue
                    consume_named(r,mn,ver,d,False)
    except Exception as e:
        d.error=f"@{r.o}:{e}"
    return d

def main():
    root=r"C:\\jww"
    for f in sorted(os.listdir(root)):
        if not f.lower().endswith(".jww"): continue
        d=parse_file(os.path.join(root,f))
        print(f"{f}: ver={d.version} L={len(d.lines)} C={len(d.circles)} T={len(d.texts)} S={len(d.solids)} P={len(d.points)} B={len(d.blocks)} err={d.error!r}")

if __name__=="__main__":
    main()
