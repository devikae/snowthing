"""collect.py 변환 테스트. 실제 디시 글 대신 구조만 흉내 낸 HTML을 쓴다(원작자 글을 저장소에 두지 않는다).

실행: python -I -m unittest discover -s scripts/column_repost
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import collect  # noqa: E402

SOURCE = "https://gall.dcinside.com/mgallery/board/view/?id=snowboard&no=7"

LIST_HTML = """
<table><tbody>
<tr class="ub-content us-post" data-no="200" data-type="icon_txt">
  <td class="gall_subject">칼럼</td>
  <td class="gall_tit ub-word"><a href="/view?no=200"><em class="icon_img"></em>직활강 팁</a> <a class="reply_numbox">[3]</a></td>
  <td class="gall_writer ub-writer" data-nick="휘구리" data-uid="wwwmskim97" data-ip=""></td>
  <td class="gall_date" title="2026-11-03 01:23:01">11.03</td>
</tr>
<tr class="ub-content us-post" data-no="199" data-type="icon_txt">
  <td class="gall_tit ub-word"><a href="/view?no=199">사칭 글</a></td>
  <td class="gall_writer ub-writer" data-nick="휘구리" data-uid="" data-ip="1.2"></td>
  <td class="gall_date" title="2026-11-02 00:00:00">11.02</td>
</tr>
</tbody></table>
"""


class ListTest(unittest.TestCase):
    def test_picks_only_fixed_nick_uid(self):
        posts = collect.parse_list(LIST_HTML, "wwwmskim97")
        self.assertEqual([(p.no, p.title, p.date) for p in posts], [(200, "직활강 팁", "2026-11-03")])


class BodyTest(unittest.TestCase):
    def convert(self, body):
        return collect.to_markdown(body, SOURCE)

    def test_lines_become_paragraphs_and_blank_lines_collapse(self):
        body = "<p>첫 줄</p><p><br></p><p><br></p><div>둘째 줄<br>셋째 줄</div>"
        self.assertEqual(self.convert(body), "첫 줄\n\n둘째 줄\n\n셋째 줄\n")

    def test_dc_image_kept_as_markdown_image(self):
        body = '<p><img src="//dcimg1.dcinside.com/viewimage.php?id=a&amp;no=b" alt="x"></p>'
        self.assertEqual(self.convert(body), "![](https://dcimg1.dcinside.com/viewimage.php?id=a&no=b)\n")

    def test_lazy_image_uses_data_original(self):
        body = '<img src="/blank.gif" data-original="https://dcimg8.dcinside.co.kr/viewimage.php?id=c">'
        self.assertIn("![](https://dcimg8.dcinside.co.kr/viewimage.php?id=c)", self.convert(body))

    def test_youtube_embed_becomes_watch_url_line(self):
        body = '<p>앞</p><iframe src="https://www.youtube.com/embed/g7JDKJCsrZA?start=30"></iframe><p>뒤</p>'
        self.assertEqual(self.convert(body), "앞\n\nhttps://www.youtube.com/watch?v=g7JDKJCsrZA&t=30s\n\n뒤\n")

    def test_youtube_link_and_bare_url_become_lines(self):
        body = (
            '<p>참고 <a href="https://youtu.be/g7JDKJCsrZA">이 영상</a></p>'
            "<p>https://www.youtube.com/watch?v=abcdefghijk</p>"
        )
        self.assertEqual(
            self.convert(body),
            "참고 이 영상\n\nhttps://www.youtube.com/watch?v=g7JDKJCsrZA\n\nhttps://www.youtube.com/watch?v=abcdefghijk\n",
        )

    def test_dc_video_becomes_source_link_once(self):
        body = (
            '<iframe src="https://gall.dcinside.com/board/movie/movie_view?no=1"></iframe>'
            '<iframe src="https://gall.dcinside.com/board/movie/movie_view?no=2"></iframe>'
            "<p>설명</p>"
        )
        self.assertEqual(self.convert(body), f"[▶ 영상은 원문에서 보기]({SOURCE})\n\n설명\n")

    def test_markdown_characters_are_escaped(self):
        body = "<p># 제목 아님</p><p>*강조 아님* [링크 아님] &lt;b&gt;</p><p>- 목록 아님</p>"
        self.assertEqual(
            self.convert(body), "\\# 제목 아님\n\n\\*강조 아님\\* \\[링크 아님\\] \\<b>\n\n\\- 목록 아님\n"
        )

    def test_numbered_headings_keep_their_numbers(self):
        body = "<p>2. 꼭 단단한 장비를 써야 하나?</p><p>10) 마지막</p><p>2026년 시즌</p>"
        self.assertEqual(self.convert(body), "2\\. 꼭 단단한 장비를 써야 하나?\n\n10\\) 마지막\n\n2026년 시즌\n")

    def test_mid_line_symbols_after_inline_tag_are_not_escaped(self):
        body = "<p><b>Ex)</b> 해머 -&gt; 프리</p>"
        self.assertEqual(self.convert(body), "Ex) 해머 -> 프리\n")

    def test_source_newlines_inside_text_become_spaces(self):
        body = "<p>문장이\n  이어진다.\n다음 말</p>"
        self.assertEqual(self.convert(body), "문장이 이어진다. 다음 말\n")

    def test_same_video_twice_in_a_row_is_kept_once(self):
        body = (
            '<iframe src="https://www.youtube.com/embed/_jhFiS5vmYI"></iframe>'
            '<p><a href="https://www.youtube.com/watch?v=_jhFiS5vmYI">https://www.youtube.com/watch?v=_jhFiS5vmYI</a></p>'
        )
        self.assertEqual(self.convert(body), "https://www.youtube.com/watch?v=_jhFiS5vmYI\n")

    def test_scripts_and_app_signature_are_dropped(self):
        body = "<p>본문</p><script>alert(1)</script><p>- dc official App</p>"
        self.assertEqual(self.convert(body), "본문\n")

    def test_other_links_keep_text(self):
        body = '<p><a href="https://example.com/a">자료</a> 그리고 <a href="https://example.com/b">https://example.com/b</a></p>'
        self.assertEqual(self.convert(body), "[자료](https://example.com/a) 그리고 https://example.com/b\n")


class ViewTest(unittest.TestCase):
    def test_extracts_nested_write_div_and_title(self):
        page = (
            '<span class="title_subject">[슬라이딩턴 101] 1강 &amp; 준비</span>'
            '<div class="writing_view_box"><div class="write_div" style="x">'
            "<div><p>안쪽</p></div><p>끝</p></div><div class=\"after\">댓글</div></div>"
        )
        self.assertEqual(collect.extract_write_div(page), "<div><p>안쪽</p></div><p>끝</p>")
        self.assertEqual(collect.extract_title(page), "[슬라이딩턴 101] 1강 & 준비")

    def test_missing_body_returns_none(self):
        self.assertIsNone(collect.extract_write_div("<html></html>"))


class SqlTest(unittest.TestCase):
    def test_orders_oldest_first_and_escapes(self):
        sql = collect.build_sql([(300, "새 글", "b"), (100, "옛 글's \\", "a")])
        first = sql.index("(1, 100,")
        second = sql.index("(2, 300,")
        self.assertLess(first, second)
        self.assertIn("'옛 글''s \\\\'", sql)
        self.assertIn("INSERT IGNORE INTO `column_repost_queue`", sql)


if __name__ == "__main__":
    unittest.main()
