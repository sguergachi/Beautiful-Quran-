# Artwork sources

## `basmala_naskh_source.svg`

Traditional Naskh basmalah calligraphy as normally seen in Qur'an manuscripts.

- **Source:** [Wikimedia Commons — File:Basmala.svg](https://commons.wikimedia.org/wiki/File:Basmala.svg)
- **Author:** Baba66 (handwritten and vectorised)
- **License:** CC BY-SA 3.0 / GFDL (author's choice)
- **App asset:** adapted into `app/src/main/res/drawable/basmalah_naskh.xml`
  (paths translated/scaled for Android VectorDrawable; fills made tintable)

To regenerate the VectorDrawable after editing the SVG:

```bash
python3 tools/artwork/svg_to_basmalah_vd.py
```
