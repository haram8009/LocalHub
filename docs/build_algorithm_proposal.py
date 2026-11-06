from pathlib import Path
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(__file__).resolve().parent
doc = Document()
section = doc.sections[0]
section.page_width, section.page_height = Cm(21), Cm(29.7)
section.top_margin = Cm(1.8)
section.bottom_margin = Cm(1.7)
section.left_margin = section.right_margin = Cm(2.1)
section.header_distance = Cm(0.7)
section.footer_distance = Cm(0.8)
for name in ['Normal', 'Title', 'Heading 1', 'Heading 2', 'Footer', 'Header']:
    style = doc.styles[name]
    style.font.name = '맑은 고딕'
    style.element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'), '맑은 고딕')
    style.font.color.rgb = RGBColor(0, 0, 0)
doc.styles['Normal'].font.size = Pt(11)
doc.styles['Normal'].paragraph_format.line_spacing = Pt(16)
doc.styles['Normal'].paragraph_format.space_after = Pt(5)
doc.styles['Title'].font.size = Pt(24)
doc.styles['Title'].font.bold = True
doc.styles['Title'].paragraph_format.line_spacing = 1.1
doc.styles['Title'].paragraph_format.space_after = Pt(8)
doc.styles['Heading 2'].font.size = Pt(12)
doc.styles['Heading 2'].paragraph_format.space_before = Pt(10)
doc.styles['Heading 2'].paragraph_format.space_after = Pt(6)

plans = [
    dict(title='알고리즘 적용 기획서 #1',
    content='Jaccard 유사도와 LCS를 이용한 비슷한 여행 코스 추천 서비스',
    algorithm='Jaccard 유사도 · LCS 동적 계획법 · 가중 합산 및 정렬',
    overview='Jaccard 유사도는 두 집합의 교집합 크기를 합집합 크기로 나누어 장소 구성이 얼마나 비슷한지 측정한다. LCS는 두 방문 목록에서 순서를 유지하면서 일치하는 가장 긴 부분 수열의 길이를 구한다. 중간 장소를 건너뛸 수 있으므로 연속된 방문 구간과는 다르다. 두 지표를 함께 사용하여 장소 구성과 방문 순서를 모두 반영한다.',
    service='게시글 상세의 비슷한 여행 코스 추천',
    paragraphs=[
    '사용자가 보고 있는 여행 코스와 장소 구성 및 방문 순서가 비슷한 다른 게시글을 제안한다. 개인의 장기 취향을 학습하는 방식이 아니라 현재 게시글을 기준으로 비교하는 콘텐츠 유사도 추천이다. Vue가 상세 조회 후 추천 API를 호출하면 Java 서버에서 후보 선정과 점수 계산을 수행한다.',
    '서버는 기준 게시글에 저장된 관광지 contentId를 visitOrder 순서로 읽고, 같은 ID를 하나 이상 공유하는 다른 게시글을 DB에서 찾는다. 현재 게시글은 후보에서 제외하며, 이름이나 좌표가 비슷하다는 이유로 다른 ID를 합치지 않는다. 계산 전 null·빈 ID와 중복 장소를 제거하고 첫 등장 순서를 유지한다.',
    'CourseSimilarityCalculator에서 HashSet으로 공통 장소 수와 합집합 크기를 계산한다. LCS는 일치하면 이전 대각선 값에 1을 더하고, 불일치하면 위·왼쪽 결과 중 큰 값을 선택한다. 이전 행과 현재 행 두 개만 저장하여 시간 O(MN), 추가 공간 O(min(M,N))으로 길이를 계산한다. 순서 유사도는 LCS 길이를 두 코스 중 더 긴 길이로 나눈 값이다.',
    '최종 점수는 Jaccard 60%와 순서 유사도 40%를 합산한다. 모든 후보를 점수 내림차순으로 정렬하고 동점이면 생성일, ID가 큰 순서로 정렬한다. 기본 상위 3개와 추천 이유를 반환한다. Vue는 유사도 백분율, 공통 관광지 수, 같은 순서의 장소 수를 카드로 표시하고 클릭 시 해당 게시글로 이동한다.'
    ],
    example='계산 예시  기준 A→B→C→D, 후보 A→B→E→D: Jaccard=3/5, LCS=3, 순서 유사도=3/4이므로 최종 점수는 0.66이다. 같은 장소를 완전히 역순으로 방문하면 0.70으로, 현재 가중치는 장소 구성을 더 중시한다.',
    evidence='구현: CourseSimilarityCalculator · CourseRecommendationService · PostRepository · PostDetailView.vue\nAPI: GET /api/posts/{postId}/similar?limit=3\n검증 코드: CourseSimilarityCalculatorTest · CourseRecommendationIntegrationTest'),
    dict(title='알고리즘 적용 기획서 #2',
    content='부분 포함 역색인과 집합 교집합을 이용한 게시글 다중 검색 서비스',
    algorithm='문자열 정규화 · 부분 포함 역색인 · HashSet 교집합 · 관련도 정렬',
    overview='역색인은 단어에서 해당 단어를 포함한 문서 목록을 찾는 자료구조이다. 이 프로젝트에서는 단어와 게시글 ID의 연결을 H2에 저장하고, 검색어를 부분 문자열로 포함하는 단어를 조회한다. 각 검색어의 게시글 집합에 교집합을 적용하여 모든 검색어를 포함한 글만 남기며, 제목·본문 일치 여부에 가중치를 주어 순위를 정한다.',
    service='게시판의 여러 검색어 AND 검색과 관련도순 결과 제공',
    paragraphs=[
    '사용자가 게시판에서 여러 단어를 입력하면 제목과 본문을 함께 검색한다. 예를 들어 “부산 바다”는 두 단어가 모두 포함된 글을 반환하고, “부산”은 “부산에서”처럼 단어 일부에 포함된 경우도 찾는다. Vue는 검색어를 API와 URL에 전달하고 서버가 반환한 목록 및 페이지 정보를 표시한다.',
    'SearchTextNormalizer는 문서와 검색어에 Unicode NFKC 정규화와 소문자 변환을 적용한다. 공백·구두점·기호를 경계로 단어를 나누고 중복을 제거한다. PostSearchIndexer는 단어와 게시글 ID, 제목·본문 포함 여부를 post_search_terms 테이블에 저장한다. 게시글 등록·수정·삭제와 인덱스 변경은 같은 트랜잭션에서 처리한다.',
    '검색 시 DB에서 각 검색어를 포함하는 단어의 연결 행을 일괄 조회한다. PostSearchService가 검색어별 게시글 ID 집합을 만들고, PostingSetOperations는 크기가 작은 집합부터 retainAll로 교집합을 구한다. 같은 검색어가 여러 단어에 일치해도 제목·본문 일치 플래그를 합쳐 점수 중복을 방지한다. 작성자·카테고리·장소 조건도 적용한다.',
    '검색어마다 제목에 있으면 2점, 본문에 있으면 1점을 더한다. 관련도 내림차순, 생성일 내림차순, ID 내림차순으로 전체 순위를 결정한 후 페이지를 나눈다. 최초 실행에서는 기존 게시글의 인덱스를 구축하고 같은 버전에서는 재사용한다. 빈 검색어는 최신순 목록으로 처리한다. 부분 포함 조회는 사전 스캔 비용이 있어 DB LIKE보다 항상 빠르다고 보장하지 않는다.'
    ],
    example='검색 예시  제목 “부산 부산여행”, 본문 “부산에서 바다를”에서 “부산”은 제목 2점+본문 1점=3점이다. 중복 검색어 “부산 부산”도 3점이며, “부산 바다”는 두 검색어의 게시글 집합을 교차한다.',
    evidence='구현: SearchTextNormalizer · PostSearchIndexer · PostingSetOperations · PostSearchService\nAPI: GET /api/posts/search?query=부산%20바다&page=1&size=10\n검증 코드: SearchAlgorithmsTest · PostSearchIntegrationTest'),
    dict(title='알고리즘 적용 기획서 #3',
    content='Argon2id를 이용한 회원 비밀번호 저장 및 기존 해시 전환 서비스',
    algorithm='Argon2id 비밀번호 해싱 · 무작위 Salt · 기존 BCrypt 검증 및 재해싱',
    overview='Argon2id는 연산 시간과 메모리 사용량을 설정할 수 있는 비밀번호 해싱 알고리즘이다. 비밀번호에 무작위 Salt를 적용하여 같은 비밀번호라도 서로 다른 저장값을 만든다. 원문 복원을 목적으로 하는 암호화와 달리 로그인 시 입력값을 저장 해시와 검증한다. 직접 암호 알고리즘을 구현하지 않고 Spring Security 인코더를 사용한다.',
    service='회원가입·로그인·비밀번호 변경 및 재설정의 서버 비밀번호 관리',
    paragraphs=[
    '회원 비밀번호를 서버에서 단방향 해시로 저장하고, 기존 BCrypt 계정도 계속 로그인할 수 있도록 한다. MigratingPasswordEncoder를 PasswordEncoder로 연결해 새 저장값에는 Argon2id를 사용하고 기존 저장값에는 해당 방식의 검증기를 적용한다. Vue는 회원 입력과 결과 표시를 담당하며 서버 해시를 보관하거나 응답으로 받지 않는다.',
    '새 비밀번호는 Salt 16바이트, 해시 32바이트, 메모리 19,456 KiB, 반복 2회, 병렬도 1로 인코딩한다. 저장 문자열에는 {argon2id-v1} 식별자와 알고리즘 설정, Salt, 해시가 포함된다. 원문에 trim이나 Unicode 정규화를 적용하지 않고 encode를 한 번 호출한다. 반복과 메모리 비용은 Argon2id 내부 설정으로 처리한다.',
    '로그인·비밀번호 변경·재설정·탈퇴 확인은 matches로 입력값을 검증한다. 기존 접두사 없는 BCrypt와 {bcrypt} 저장값도 허용하며, 올바른 비밀번호로 로그인한 경우에만 Argon2id로 재해싱하여 같은 회원 레코드에 저장한다. 비밀번호를 모르는 상태에서 기존 해시를 새 해시로 변환하지 않으며, 로그인 실패 시 저장값을 유지한다.',
    '손상된 해시나 알 수 없는 식별자는 인증 실패로 처리한다. 로그인 재해싱과 회원 변경·탈퇴는 회원 행 잠금으로 직렬화하여 갱신 충돌을 제어한다. 인코딩 방식만 갱신할 때는 기존 세션을 끊지 않고, 비밀번호 자체를 바꾸면 기존 credentialVersion 정책을 따른다. 검증 코드는 무작위 Salt, 정상·오류 입력, 설정값, 기존 회원 전환 및 잘못된 로그인 시 해시 유지를 확인한다.'
    ],
    example='적용 예시  같은 비밀번호로 두 번 가입해도 Salt가 달라 저장 문자열이 다르다. BCrypt 계정은 로그인 성공 후 Argon2id로 갱신된다. 메모리·반복 비용은 추측 공격의 비용을 높이기 위한 설정이며 로그인 속도 개선이 목적은 아니다.',
    evidence='구현: MigratingPasswordEncoder · SecurityConfig · AuthController\n라이브러리: Spring Security Argon2PasswordEncoder · Bouncy Castle\n검증 코드: MigratingPasswordEncoderTest · ApiIntegrationTest')
]

def paragraph(text='', size=None, bold=False, after=None):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.bold = bold
    if size:
        r.font.size = Pt(size)
    if after is not None:
        p.paragraph_format.space_after = Pt(after)
    return p


def labeled(label, text):
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing = Pt(14)
    p.paragraph_format.space_after = Pt(4)
    p.add_run(label + '  ').bold = True
    p.add_run(text)
    for r in p.runs:
        r.font.size = Pt(9.5)
    return p


def metrics(labels, values):
    table = doc.add_table(rows=2, cols=len(labels))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    for row_i, texts in enumerate((labels, values)):
        for j, text in enumerate(texts):
            c = table.cell(row_i, j)
            c.width = Cm(16.8 / len(labels))
            c.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            tcpr = c._tc.get_or_add_tcPr()
            shade = OxmlElement('w:shd')
            shade.set(qn('w:fill'), 'EEF5F2')
            tcpr.append(shade)
            margins = OxmlElement('w:tcMar')
            for edge, value in [('top',75),('bottom',65),('left',140),('right',140)]:
                item = OxmlElement('w:' + edge)
                item.set(qn('w:w'), str(value))
                item.set(qn('w:type'), 'dxa')
                margins.append(item)
            tcpr.append(margins)
            p = c.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.0
            r = p.add_run(text)
            r.font.size = Pt(9 if row_i == 0 else 18)
            r.bold = row_i == 1
            r.font.color.rgb = RGBColor.from_string('000000' if row_i == 0 else '287559')
    marker = OxmlElement('w:tblHeader')
    table.rows[0]._tr.get_or_add_trPr().append(marker)
    for row in table.rows:
        row._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
    # Keep both rows together while allowing ordinary body paragraphs to flow.
    for c in table.rows[0].cells:
        c.paragraphs[0].paragraph_format.keep_with_next = True


titles = ['비슷한 여행 코스 추천', '여러 검색어로 게시글 찾기', '회원 비밀번호 안전하게 저장하기']
summaries = [
    '장소 구성과 방문 순서를 함께 비교해 다른 여행 코스를 제안한다.',
    '부분 포함 검색과 교집합으로 모든 검색어를 포함한 글을 찾는다.',
    'Argon2id로 새 비밀번호를 저장하고 기존 BCrypt 계정을 전환한다.'
]
metric_data = [
    (['장소 유사도 가중치', '방문 순서 가중치', '기본 추천 개수'], ['60%', '40%', '3개']),
    (['검색어별 제목 일치', '검색어별 본문 일치', '중복 검색어 추가 점수'], ['2점', '1점', '0점']),
    (['무작위 Salt', '작업 메모리', '반복 횟수'], ['16 bytes', '19 MiB', '2회'])
]
for i, plan in enumerate(plans):
    p = paragraph(plan['title'], size=10, bold=True, after=8)
    if i:
        p.paragraph_format.page_break_before = True
    p.paragraph_format.keep_with_next = True
    doc.add_paragraph(titles[i], 'Title')
    labeled('내용', plan['content'])
    labeled('적용 알고리즘', plan['algorithm'])
    labeled('적용 서비스', plan['service'])
    doc.add_paragraph('01  알고리즘 개요', 'Heading 2')
    doc.add_paragraph(plan['overview'])
    metrics(*metric_data[i])
    doc.add_paragraph('02  적용 서비스 개발 개요', 'Heading 2')
    for text in plan['paragraphs']:
        doc.add_paragraph(text)
    doc.add_paragraph('03  적용 예시', 'Heading 2')
    p = paragraph(plan['example'].split('  ',1)[-1], size=10, after=7)
    p.paragraph_format.line_spacing = Pt(14)
    p = paragraph(plan['evidence'], size=8, after=0)
    p.paragraph_format.line_spacing = Pt(11)

p = paragraph('서비스 적용 화면', size=10, bold=True, after=8)
p.paragraph_format.page_break_before = True
doc.add_paragraph('화면에서 확인하는 알고리즘', 'Title')
paragraph('데스크톱 1440px 환경에서 캡처한 개발용 예제 데이터 화면', size=10, after=8)
for title, filename, caption in [
    ('01  비슷한 여행 코스 추천', 'course-recommendation-desktop.jpg',
     '같은 동선은 100%, 공통 장소 3곳과 LCS 2곳인 동선은 87%로 표시된다.'),
    ('02  게시글 다중 검색', 'post-search-desktop.jpg',
     '“역삼 카페”를 모두 포함한 글을 찾고 제목·본문 일치에 따른 관련도를 표시한다.')
]:
    doc.add_paragraph(title, 'Heading 2')
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.line_spacing = 1.0
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.keep_with_next = True
    p.add_run().add_picture(str(ROOT/'images'/filename), width=Cm(13.5))
    caption_p = paragraph(caption, size=9, after=7)
    caption_p.paragraph_format.line_spacing = Pt(12)

header = section.header.paragraphs[0]
header.paragraph_format.space_after = Pt(0)
header.paragraph_format.tab_stops.add_tab_stop(Cm(16.8), WD_ALIGN_PARAGRAPH.RIGHT)
hr = header.add_run('EnjoyTrip  /  LocalHub\tALGORITHM PROPOSAL')
hr.font.size = Pt(8)
hr.bold = True

footer = section.footer.paragraphs[0]
footer.alignment = 2
run = footer.add_run('구현 코드 기준 2026.10.15    /    ')
run.font.size = Pt(8)
field = OxmlElement('w:fldSimple')
field.set(qn('w:instr'), 'PAGE')
footer._p.append(field)
for style in doc.styles:
    for border in list(style.element.iter(qn('w:pBdr'))):
        border.getparent().remove(border)
for p in list(doc.paragraphs) + [p for t in doc.tables for row in t.rows for c in row.cells for p in c.paragraphs]:
    for border in list(p._p.iter(qn('w:pBdr'))):
        border.getparent().remove(border)
    grid = OxmlElement('w:snapToGrid')
    grid.set(qn('w:val'), '0')
    p._p.get_or_add_pPr().append(grid)
doc.core_properties.title = 'EnjoyTrip 알고리즘 적용 기획서'
doc.core_properties.subject = '여행 코스 추천 게시글 검색 회원 비밀번호 해싱'
doc.core_properties.author = 'EnjoyTrip'
output = ROOT / '알고리즘_적용_기획서.docx'
doc.save(output)
print(output)
