package com.portofino.realtrainmodunofficial.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * パックスクリプトの TypeScript 対応。<b>型を消して JavaScript にするだけ</b>の変換器。
 *
 * <h2>なぜ型を消すだけで足りるのか</h2>
 * TypeScript は「JavaScript に型注釈を足したもの」なので、<b>注釈を取り除けばそのまま動く
 * JavaScript になる</b> (公式の {@code isolatedModules} / Node の {@code --strip-types} と同じ考え方)。
 * 型検査はエディタ側 (tsc / VSCode) がやればよく、ゲーム内では要らない。
 *
 * <h2>なぜ tsc を積まないのか</h2>
 * 本物の TypeScript コンパイラは 10 MB 超の JavaScript で、Nashorn の上で走らせると
 * パック 1 個の読み込みに数秒かかる。jar も一気に膨らむ。<b>型を消すだけ</b>ならこのファイル 1 枚で済み、
 * 依存も増えず、実測でスクリプト 1 本あたり 1 ミリ秒未満で終わる。
 *
 * <h2>既存の .js には一切触らない</h2>
 * 変換するのは <b>{@code .ts} 拡張子のファイルだけ</b> ({@link #isTypeScript})。
 * {@code .js} は 1 バイトも変えずに今までどおりの経路を通る。既存パックへの影響はゼロ。
 *
 * <h2>消したところは空白で埋める</h2>
 * 単純に削ると行と桁がずれ、Nashorn の例外に出る行番号が元のファイルと合わなくなる。
 * <b>改行だけ残して空白に置き換える</b>ので、行番号はそのまま使える。
 *
 * <h2>対応している構文</h2>
 * <ul>
 *   <li>型注釈 — 変数・引数・戻り値・クラスのプロパティ</li>
 *   <li>ジェネリクス — 宣言側 {@code function f<T>()} も呼び出し側 {@code f<T>()} も</li>
 *   <li>{@code interface} / {@code type} / {@code declare} / {@code import type}</li>
 *   <li>{@code as} / {@code satisfies} / 非 null 表明 {@code x!}</li>
 *   <li>省略可能 {@code a?: T} / 修飾子 {@code public private protected readonly abstract override}</li>
 *   <li>{@code implements} 節 / デコレータ {@code @Foo(...)}</li>
 *   <li>{@code enum} — TypeScript と同じ形 (数値なら逆引きつき) に展開する</li>
 *   <li>コンストラクタの引数プロパティ {@code constructor(private a: T)} — {@code this.a = a} を足す</li>
 * </ul>
 *
 * <p><b>未対応</b>: {@code namespace} / {@code module} と ES モジュールの
 * {@code import} / {@code export from}。パックスクリプトは 1 ファイルが 1 つのグローバル空間で、
 * 読み込みは {@code //include <...>} で行うため。使われていたら {@link #diagnose} が理由を返す。
 */
public final class TypeScriptTranspiler {

    private TypeScriptTranspiler() {
    }

    /** 拡張子で判定する。ここが「.js は絶対に触らない」の担保。 */
    public static boolean isTypeScript(String path) {
        if (path == null) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT);
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        return p.endsWith(".ts") || p.endsWith(".mts") || p.endsWith(".cts");
    }

    /**
     * {@code .js} を探して見つからないときに試す {@code .ts} 側のパス。
     * パック側が {@code "rendererPath": "scripts/Foo.js"} のまま {@code Foo.ts} を同梱していても動く。
     */
    public static String toTypeScriptPath(String jsPath) {
        if (jsPath == null || !jsPath.toLowerCase(Locale.ROOT).endsWith(".js")) {
            return null;
        }
        return jsPath.substring(0, jsPath.length() - 3) + ".ts";
    }

    /** 対応していない構文が使われていたら理由を返す。無ければ null。 */
    public static String diagnose(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.lex();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type != TokenType.WORD) {
                continue;
            }
            if ((t.is("namespace") || t.is("module")) && isStatementStart(tokens, i)) {
                return "namespace / module は未対応です (パックスクリプトは 1 ファイル 1 グローバル空間のため)。"
                    + "素のオブジェクトか //include <...> を使ってください";
            }
            if (t.is("import") && isStatementStart(tokens, i)) {
                Token next = next(tokens, i);
                if (next != null && next.is("type")) {
                    continue;   //import type は消せるので問題ない
                }
                return "ES モジュールの import は未対応です。//include <ファイル名> を使ってください";
            }
            if (t.is("function") && next(tokens, i) != null && next(tokens, i).is("*")) {
                return "ジェネレータ関数 (function*) はスクリプトエンジンが未対応です";
            }
        }
        //★ここから下はスクリプトエンジン (Nashorn) 側の制限。
        //  変換で消せる類ではないので、素通しして意味の分からない構文エラーを出すより
        //  「何が使えないか」を先に伝えたほうがパック作者が直せる。
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type != TokenType.PUNCT) {
                continue;
            }
            if (t.is("?.")) {
                return "?. (optional chaining) はスクリプトエンジンが未対応です。x && x.y と書いてください";
            }
            if (t.is("??")) {
                return "?? (null 合体) はスクリプトエンジンが未対応です。x != null ? x : y と書いてください";
            }
            if (t.is("**") || t.is("**=")) {
                return "** (べき乗) はスクリプトエンジンが未対応です。Math.pow(a, b) を使ってください";
            }
            if (t.is("...")) {
                //引数リストの ... は変換で消えるので、残るのは呼び出し側と分割代入
                Token p = prev(tokens, i);
                if (p != null && (p.is("(") || p.is(",")) && !isParameterListRest(tokens, i)) {
                    return "呼び出し側のスプレッド f(...a) はスクリプトエンジンが未対応です。"
                        + "f.apply(null, a) を使ってください";
                }
                if (p != null && (p.is("[") || p.is("{"))) {
                    return "分割代入はスクリプトエンジンが未対応です";
                }
            }
            if (t.is("[") || t.is("{")) {
                //var [a, b] = ... / var {a} = ...
                Token p = prev(tokens, i);
                if (p != null && p.type == TokenType.WORD
                        && (p.is("var") || p.is("let") || p.is("const"))) {
                    return "分割代入はスクリプトエンジンが未対応です。個別に代入してください";
                }
            }
        }
        return null;
    }

    /** その {@code ...} が「関数の引数リストの可変長引数」か (= 変換で消せるか)。 */
    private static boolean isParameterListRest(List<Token> tokens, int i) {
        int depth = 0;
        for (int j = i - 1; j >= 0; j--) {
            Token u = tokens.get(j);
            if (u.type != TokenType.PUNCT) {
                continue;
            }
            if (u.is(")") || u.is("]") || u.is("}")) {
                depth++;
            } else if (u.is("(")) {
                if (depth == 0) {
                    int close = j;
                    int d2 = 0;
                    for (int k = j; k < tokens.size(); k++) {
                        Token v = tokens.get(k);
                        if (v.type != TokenType.PUNCT) {
                            continue;
                        }
                        if (v.is("(")) {
                            d2++;
                        } else if (v.is(")")) {
                            d2--;
                            if (d2 == 0) {
                                close = k;
                                break;
                            }
                        }
                    }
                    Token after = next(tokens, close);
                    //★戻り値の型注釈 ")" : T " {" を挟むので ":" も認める。
                    //  ここを "{" だけにすると、型つきメソッドの可変長引数を
                    //  「呼び出し側のスプレッド」と誤検知する。
                    return after != null && (after.is("{") || after.is(":") || after.is("=>"));
                }
                depth--;
            } else if (u.is("[") || u.is("{")) {
                depth--;
            }
        }
        return false;
    }

    /**
     * TypeScript を JavaScript にする。
     *
     * @param source TypeScript のソース
     * @return 型を取り除いた JavaScript。行番号は元のまま
     */
    public static String toJavaScript(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        String stripped = new Rewriter(source, new Lexer(source).lex()).run();
        //★2 段目: ES6 の class を ES5 のプロトタイプ形式へ落とす。
        //  Nashorn は class を実装していない ("ES6 class declarations ... not yet implemented")。
        //  型を消しただけでは TypeScript らしいコードが動かないので、ここまでやって初めて使える。
        return ClassLowering.run(stripped);
    }

    /**
     * ES6 の {@code class} を ES5 のプロトタイプ形式へ落とす。
     *
     * <p>Nashorn は {@code class} を実装していないため、これが無いと
     * TypeScript でクラスを書いた瞬間に構文エラーになる。
     *
     * <p><b>行番号を保つ</b>のが設計の要。置き換えは<b>各行の中で完結</b>させ、
     * メソッド本体はソースの位置をそのまま残す。
     * <pre>
     *   class C extends B {          →  var C = (function (_super) { __extends(C, _super);
     *     constructor(a) {           →    function C(a) {
     *       super(a);                →      _super.call(this, a);
     *     }                          →    }
     *     m() {                      →    C.prototype.m = function () {
     *       return super.m();        →      return _super.prototype.m.call(this);
     *     }                          →    };
     *   }                            →    return C; })(B);
     * </pre>
     */
    static final class ClassLowering {

        private ClassLowering() {
        }

        static String run(String src) {
            if (src == null || src.indexOf("class") < 0) {
                return src;
            }
            List<Token> tokens = new Lexer(src).lex();
            StringBuilder work = new StringBuilder(src);
            List<Edit> edits = new ArrayList<>();
            boolean any = false;
            if (lowerRestParams(src, tokens, edits)) {
                any = true;
            }
            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.type == TokenType.WORD && t.is("class")) {
                    Token p = prev(tokens, i);
                    //プロパティ名やメンバ名の "class" は対象外
                    if (p != null && (p.is(".") || p.is(":"))) {
                        continue;
                    }
                    int end = lowerOne(src, tokens, i, edits);
                    if (end > i) {
                        any = true;
                        i = end;
                    }
                }
            }
            if (!any) {
                return src;
            }
            boolean needsExtends = false;
            for (Edit e : edits) {
                if (e.text.contains("__extends(")) {
                    needsExtends = true;
                    break;
                }
            }
            edits.sort((a, b) -> Integer.compare(b.start, a.start));
            for (Edit e : edits) {
                work.replace(e.start, e.end, e.text);
            }
            //★改行を足さずに 1 行目の先頭へ置く。改行を入れると以降の行番号が 1 ずれ、
            //  Nashorn の例外に出る行が元のファイルと合わなくなる。
            return needsExtends ? HELPER + work : work.toString();
        }

        /**
         * 可変長引数 {@code function f(a, ...rest)} を ES5 化する。
         *
         * <p>Nashorn は rest parameter を実装していない。引数リストから消して、
         * 本体の先頭で {@code arguments} から切り出す。
         * <b>アロー関数は対象外</b> — アローは {@code arguments} を持たないので、
         * 同じ手が使えない ({@link #diagnose} で知らせる)。
         */
        private static boolean lowerRestParams(String src, List<Token> tokens, List<Edit> edits) {
            boolean any = false;
            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.type != TokenType.PUNCT || !t.is("...")) {
                    continue;
                }
                //この "..." を含む括弧グループの開き位置を探す
                int open = -1;
                int depth = 0;
                for (int j = i - 1; j >= 0; j--) {
                    Token u = tokens.get(j);
                    if (u.type != TokenType.PUNCT) {
                        continue;
                    }
                    if (u.is(")") || u.is("]") || u.is("}")) {
                        depth++;
                    } else if (u.is("(")) {
                        if (depth == 0) {
                            open = j;
                            break;
                        }
                        depth--;
                    } else if (u.is("[") || u.is("{")) {
                        depth--;
                    }
                }
                if (open < 0) {
                    continue;
                }
                int close = matchPair(tokens, open, "(", ")");
                int body = close < 0 ? -1 : nextIndex(tokens, close);
                if (body < 0 || !tokens.get(body).is("{")) {
                    continue;   //呼び出し側のスプレッドなど。ここでは扱わない
                }
                int nameIdx = nextIndex(tokens, i);
                if (nameIdx < 0 || tokens.get(nameIdx).type != TokenType.WORD) {
                    continue;
                }
                //先行する引数の数 = 深さ 0 のカンマの数
                int count = 0;
                int d = 0;
                for (int j = open + 1; j < i; j++) {
                    Token u = tokens.get(j);
                    if (u.type != TokenType.PUNCT) {
                        continue;
                    }
                    if (u.is("(") || u.is("[") || u.is("{")) {
                        d++;
                    } else if (u.is(")") || u.is("]") || u.is("}")) {
                        d--;
                    } else if (d == 0 && u.is(",")) {
                        count++;
                    }
                }
                //直前のカンマごと消す (最初の引数なら "..." から)
                int from = t.start;
                int prevComma = prevIndex(tokens, i);
                if (prevComma >= 0 && tokens.get(prevComma).is(",")) {
                    from = tokens.get(prevComma).start;
                }
                edits.add(new Edit(from, tokens.get(nameIdx).end, ""));
                edits.add(new Edit(tokens.get(body).end, tokens.get(body).end,
                    " var " + tokens.get(nameIdx).text
                    + " = Array.prototype.slice.call(arguments, " + count + ");"));
                any = true;
            }
            return any;
        }

        /** {@code extends} 用のヘルパー。tsc が出すものと同じ形。 */
        private static final String HELPER =
            "var __extends = __extends || function (d, b) { for (var k in b) "
            + "if (Object.prototype.hasOwnProperty.call(b, k)) d[k] = b[k]; "
            + "function T() { this.constructor = d; } T.prototype = b.prototype; "
            + "d.prototype = b === null ? Object.create(b) : (T.prototype = b.prototype, new T()); };";

        private record Edit(int start, int end, String text) { }

        /** クラス 1 つを落とす。戻り値は消費した最後のトークン添字。 */
        private static int lowerOne(String src, List<Token> tokens, int classIdx, List<Edit> edits) {
            int i = nextIndex(tokens, classIdx);
            String name = null;
            if (i >= 0 && tokens.get(i).type == TokenType.WORD && !tokens.get(i).is("extends")) {
                name = tokens.get(i).text;
                i = nextIndex(tokens, i);
            }
            if (name == null) {
                return classIdx;   //無名クラスは対象外
            }
            String superExpr = null;
            if (i >= 0 && tokens.get(i).is("extends")) {
                int from = nextIndex(tokens, i);
                int brace = from;
                while (brace < tokens.size() && !tokens.get(brace).is("{")) {
                    brace++;
                }
                if (brace >= tokens.size()) {
                    return classIdx;
                }
                superExpr = src.substring(tokens.get(from).start, tokens.get(brace).start).trim();
                i = brace;
            }
            while (i >= 0 && i < tokens.size() && !tokens.get(i).is("{")) {
                i = nextIndex(tokens, i);
            }
            if (i < 0 || i >= tokens.size()) {
                return classIdx;
            }
            int open = i;
            int close = matchPair(tokens, open, "{", "}");
            if (close < 0) {
                return classIdx;
            }

            //--- ヘッダ ---
            StringBuilder header = new StringBuilder("var ").append(name).append(" = (function (");
            if (superExpr != null) {
                header.append("_super");
            }
            header.append(") {");
            if (superExpr != null) {
                header.append(" __extends(").append(name).append(", _super);");
            }
            edits.add(new Edit(tokens.get(classIdx).start, tokens.get(open).end, header.toString()));

            boolean sawCtor = false;
            //--- メンバ ---
            int j = nextIndex(tokens, open);
            while (j > 0 && j < close) {
                Token t = tokens.get(j);
                if (t.is(";")) {
                    j = nextIndex(tokens, j);
                    continue;
                }
                boolean isStatic = false;
                boolean isGet = false;
                boolean isSet = false;
                int memberStart = t.start;
                while (j > 0 && j < close && tokens.get(j).type == TokenType.WORD) {
                    Token n = next(tokens, j);
                    if (tokens.get(j).is("static") && n != null && n.type != TokenType.PUNCT) {
                        isStatic = true;
                        j = nextIndex(tokens, j);
                        continue;
                    }
                    if ((tokens.get(j).is("get") || tokens.get(j).is("set"))
                            && n != null && (n.type == TokenType.WORD || n.type == TokenType.STRING)) {
                        isGet = tokens.get(j).is("get");
                        isSet = tokens.get(j).is("set");
                        j = nextIndex(tokens, j);
                        continue;
                    }
                    break;
                }
                if (j < 0 || j >= close) {
                    break;
                }
                Token nameTok = tokens.get(j);
                String member = nameTok.type == TokenType.STRING
                    ? nameTok.text.substring(1, nameTok.text.length() - 1) : nameTok.text;
                int paren = nextIndex(tokens, j);
                if (paren < 0 || !tokens.get(paren).is("(")) {
                    j = nextIndex(tokens, j);
                    continue;
                }
                int parenClose = matchPair(tokens, paren, "(", ")");
                int body = parenClose < 0 ? -1 : nextIndex(tokens, parenClose);
                if (body < 0 || !tokens.get(body).is("{")) {
                    j = nextIndex(tokens, j);
                    continue;
                }
                int bodyClose = matchPair(tokens, body, "{", "}");
                if (bodyClose < 0) {
                    break;
                }
                String params = src.substring(tokens.get(paren).start, tokens.get(parenClose).end);

                if ("constructor".equals(member)) {
                    sawCtor = true;
                    edits.add(new Edit(memberStart, tokens.get(paren).start,
                        "function " + name + " "));
                } else if (isGet || isSet) {
                    String target = isStatic ? name : name + ".prototype";
                    edits.add(new Edit(memberStart, tokens.get(body).start,
                        "Object.defineProperty(" + target + ", \"" + member + "\", { "
                        + (isGet ? "get" : "set") + ": function " + params + " "));
                    edits.add(new Edit(tokens.get(bodyClose).start, tokens.get(bodyClose).end,
                        "}, configurable: true });"));
                } else {
                    String target = isStatic ? name + "." : name + ".prototype.";
                    edits.add(new Edit(memberStart, tokens.get(paren).start,
                        target + member + " = function "));
                    edits.add(new Edit(tokens.get(bodyClose).start, tokens.get(bodyClose).end, "};"));
                }
                //super の書き換え
                if (superExpr != null) {
                    rewriteSuper(src, tokens, body, bodyClose, edits);
                }
                j = nextIndex(tokens, bodyClose);
            }

            //--- コンストラクタが無ければ足す ---
            StringBuilder tail = new StringBuilder();
            if (!sawCtor) {
                tail.append(" function ").append(name).append("() {");
                if (superExpr != null) {
                    tail.append(" _super.apply(this, arguments);");
                }
                tail.append(" }");
                edits.add(new Edit(tokens.get(open).end, tokens.get(open).end, tail.toString()));
            }
            //--- 末尾 ---
            edits.add(new Edit(tokens.get(close).start, tokens.get(close).end,
                " return " + name + "; })(" + (superExpr == null ? "" : superExpr) + ");"));
            return close;
        }

        /** メソッド本体の {@code super(...)} / {@code super.m(...)} を書き換える。 */
        private static void rewriteSuper(String src, List<Token> tokens, int from, int to,
                                         List<Edit> edits) {
            for (int k = from; k < to; k++) {
                Token t = tokens.get(k);
                if (t.type != TokenType.WORD || !t.is("super")) {
                    continue;
                }
                int n = nextIndex(tokens, k);
                if (n < 0) {
                    continue;
                }
                if (tokens.get(n).is("(")) {
                    //super(a, b) → _super.call(this, a, b)
                    int c = matchPair(tokens, n, "(", ")");
                    boolean empty = c > 0 && nextIndex(tokens, n) == c;
                    edits.add(new Edit(t.start, tokens.get(n).end,
                        "_super.call(this" + (empty ? "" : ", ")));
                } else if (tokens.get(n).is(".")) {
                    int m = nextIndex(tokens, n);
                    if (m < 0) {
                        continue;
                    }
                    int callOpen = nextIndex(tokens, m);
                    if (callOpen > 0 && tokens.get(callOpen).is("(")) {
                        int c = matchPair(tokens, callOpen, "(", ")");
                        boolean empty = c > 0 && nextIndex(tokens, callOpen) == c;
                        edits.add(new Edit(t.start, tokens.get(callOpen).end,
                            "_super.prototype." + tokens.get(m).text + ".call(this"
                            + (empty ? "" : ", ")));
                    } else {
                        edits.add(new Edit(t.start, t.end, "_super.prototype"));
                    }
                }
            }
        }

        private static int matchPair(List<Token> tokens, int i, String open, String close) {
            int depth = 0;
            for (int j = i; j < tokens.size(); j++) {
                Token t = tokens.get(j);
                if (t.type != TokenType.PUNCT) {
                    continue;
                }
                if (t.is(open)) {
                    depth++;
                } else if (t.is(close)) {
                    depth--;
                    if (depth == 0) {
                        return j;
                    }
                }
            }
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // 字句解析
    // ------------------------------------------------------------------

    private enum TokenType { WORD, NUMBER, STRING, TEMPLATE, REGEX, PUNCT, COMMENT, WS }

    private static final class Token {
        final TokenType type;
        final String text;
        final int start;
        final int end;

        Token(TokenType type, String text, int start, int end) {
            this.type = type;
            this.text = text;
            this.start = start;
            this.end = end;
        }

        boolean is(String s) {
            return this.text.equals(s);
        }

        @Override
        public String toString() {
            return this.type + "(" + this.text + ")";
        }
    }

    /**
     * JavaScript/TypeScript の字句解析。
     *
     * <p>文字列・テンプレートリテラル・正規表現・コメントを正しく飛ばすのが目的。
     * ここを手を抜いて正規表現で済ませると、文字列の中の {@code ": "} を型注釈と誤認して壊す。
     */
    private static final class Lexer {
        private final String src;
        private int pos;
        private final List<Token> out = new ArrayList<>();

        Lexer(String src) {
            this.src = src;
        }

        List<Token> lex() {
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos);
                if (c == '/' && this.peek(1) == '/') {
                    this.lineComment();
                } else if (c == '/' && this.peek(1) == '*') {
                    this.blockComment();
                } else if (c == '"' || c == '\'') {
                    this.string(c);
                } else if (c == '`') {
                    this.template();
                } else if (c == '/' && this.regexAllowed()) {
                    this.regex();
                } else if (Character.isWhitespace(c)) {
                    this.whitespace();
                } else if (Character.isDigit(c)
                        || (c == '.' && Character.isDigit(this.peek(1)))) {
                    this.number();
                } else if (isIdentStart(c)) {
                    this.word();
                } else {
                    this.punct();
                }
            }
            return this.out;
        }

        private char peek(int off) {
            int i = this.pos + off;
            return i < this.src.length() ? this.src.charAt(i) : '\0';
        }

        private void add(TokenType type, int start) {
            this.out.add(new Token(type, this.src.substring(start, this.pos), start, this.pos));
        }

        private void lineComment() {
            int start = this.pos;
            while (this.pos < this.src.length() && this.src.charAt(this.pos) != '\n') {
                this.pos++;
            }
            this.add(TokenType.COMMENT, start);
        }

        private void blockComment() {
            int start = this.pos;
            this.pos += 2;
            while (this.pos < this.src.length()
                    && !(this.src.charAt(this.pos) == '*' && this.peek(1) == '/')) {
                this.pos++;
            }
            this.pos = Math.min(this.src.length(), this.pos + 2);
            this.add(TokenType.COMMENT, start);
        }

        private void string(char quote) {
            int start = this.pos;
            this.pos++;
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos);
                if (c == '\\') {
                    this.pos += 2;
                    continue;
                }
                this.pos++;
                if (c == quote || c == '\n') {
                    break;
                }
            }
            this.add(TokenType.STRING, start);
        }

        /** テンプレートリテラル。{@code ${}} の中は入れ子になるので括弧を数える。 */
        private void template() {
            int start = this.pos;
            this.pos++;
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos);
                if (c == '\\') {
                    this.pos += 2;
                    continue;
                }
                if (c == '`') {
                    this.pos++;
                    break;
                }
                if (c == '$' && this.peek(1) == '{') {
                    this.pos += 2;
                    int depth = 1;
                    while (this.pos < this.src.length() && depth > 0) {
                        char d = this.src.charAt(this.pos);
                        if (d == '{') {
                            depth++;
                        } else if (d == '}') {
                            depth--;
                        } else if (d == '`') {
                            //入れ子のテンプレートは飛ばす
                            this.pos++;
                            while (this.pos < this.src.length() && this.src.charAt(this.pos) != '`') {
                                if (this.src.charAt(this.pos) == '\\') {
                                    this.pos++;
                                }
                                this.pos++;
                            }
                        }
                        this.pos++;
                    }
                    continue;
                }
                this.pos++;
            }
            this.add(TokenType.TEMPLATE, start);
        }

        /**
         * 直前の意味のあるトークンから「ここの / は正規表現の始まりか、割り算か」を決める。
         * 判断を誤ると以降の字句が全部ずれる。
         */
        private boolean regexAllowed() {
            for (int i = this.out.size() - 1; i >= 0; i--) {
                Token t = this.out.get(i);
                if (t.type == TokenType.WS || t.type == TokenType.COMMENT) {
                    continue;
                }
                if (t.type == TokenType.NUMBER || t.type == TokenType.STRING
                        || t.type == TokenType.TEMPLATE || t.type == TokenType.REGEX) {
                    return false;
                }
                if (t.type == TokenType.WORD) {
                    //値で終わる語の後は割り算、制御構文の後は正規表現
                    return !(t.is("this") || t.is("true") || t.is("false") || t.is("null")
                        || t.is("undefined") || t.is("super"));
                }
                return !(t.is(")") || t.is("]") || t.is("}") || t.is("++") || t.is("--"));
            }
            return true;
        }

        private void regex() {
            int start = this.pos;
            this.pos++;
            boolean inClass = false;
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos);
                if (c == '\\') {
                    this.pos += 2;
                    continue;
                }
                if (c == '[') {
                    inClass = true;
                } else if (c == ']') {
                    inClass = false;
                } else if (c == '/' && !inClass) {
                    this.pos++;
                    break;
                } else if (c == '\n') {
                    break;
                }
                this.pos++;
            }
            while (this.pos < this.src.length() && isIdentPart(this.src.charAt(this.pos))) {
                this.pos++;
            }
            this.add(TokenType.REGEX, start);
        }

        private void whitespace() {
            int start = this.pos;
            while (this.pos < this.src.length() && Character.isWhitespace(this.src.charAt(this.pos))) {
                this.pos++;
            }
            this.add(TokenType.WS, start);
        }

        private void number() {
            int start = this.pos;
            while (this.pos < this.src.length()) {
                char c = this.src.charAt(this.pos);
                if (Character.isLetterOrDigit(c) || c == '.' || c == '_'
                        || ((c == '+' || c == '-') && this.pos > start
                            && (this.src.charAt(this.pos - 1) == 'e' || this.src.charAt(this.pos - 1) == 'E'))) {
                    this.pos++;
                } else {
                    break;
                }
            }
            this.add(TokenType.NUMBER, start);
        }

        private void word() {
            int start = this.pos;
            while (this.pos < this.src.length() && isIdentPart(this.src.charAt(this.pos))) {
                this.pos++;
            }
            this.add(TokenType.WORD, start);
        }

        private static final String[] PUNCTUATORS = {
            ">>>=", "...", "===", "!==", "**=", "<<=", ">>=", ">>>", "&&=", "||=", "??=",
            "=>", "==", "!=", "<=", ">=", "&&", "||", "??", "?.", "++", "--", "+=", "-=",
            "*=", "/=", "%=", "&=", "|=", "^=", "**", "<<", ">>",
        };

        private void punct() {
            int start = this.pos;
            for (String p : PUNCTUATORS) {
                if (this.src.startsWith(p, this.pos)) {
                    this.pos += p.length();
                    this.add(TokenType.PUNCT, start);
                    return;
                }
            }
            this.pos++;
            this.add(TokenType.PUNCT, start);
        }
    }

    private static boolean isMemberModifier(String s) {
        return s.equals("public") || s.equals("private") || s.equals("protected")
            || s.equals("readonly") || s.equals("abstract") || s.equals("override")
            || s.equals("declare") || s.equals("static");
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    // ------------------------------------------------------------------
    // 書き換え
    // ------------------------------------------------------------------

    /** 意味のある (空白・コメントでない) 次のトークンの添字。無ければ -1。 */
    private static int nextIndex(List<Token> tokens, int i) {
        for (int j = i + 1; j < tokens.size(); j++) {
            TokenType t = tokens.get(j).type;
            if (t != TokenType.WS && t != TokenType.COMMENT) {
                return j;
            }
        }
        return -1;
    }

    private static Token next(List<Token> tokens, int i) {
        int j = nextIndex(tokens, i);
        return j < 0 ? null : tokens.get(j);
    }

    private static int prevIndex(List<Token> tokens, int i) {
        for (int j = i - 1; j >= 0; j--) {
            TokenType t = tokens.get(j).type;
            if (t != TokenType.WS && t != TokenType.COMMENT) {
                return j;
            }
        }
        return -1;
    }

    private static Token prev(List<Token> tokens, int i) {
        int j = prevIndex(tokens, i);
        return j < 0 ? null : tokens.get(j);
    }

    /** 文の先頭にあるか (直前が {@code ; } { }} か行頭)。 */
    private static boolean isStatementStart(List<Token> tokens, int i) {
        Token p = prev(tokens, i);
        return p == null || p.is(";") || p.is("{") || p.is("}") || p.is(")");
    }

    private static final class Rewriter {
        private final char[] out;
        private final List<Token> tokens;
        /** クラス本体の波括弧の深さ (0 = クラスの外)。 */
        private final List<Integer> classBodyDepths = new ArrayList<>();
        private int depth;
        /** enum などで挿し込む断片。位置の昇順で最後にまとめて差し込む。 */
        private final List<int[]> inserts = new ArrayList<>();
        private final List<String> insertTexts = new ArrayList<>();

        Rewriter(String src, List<Token> tokens) {
            this.out = src.toCharArray();
            this.tokens = tokens;
        }

        /** 範囲を空白で潰す。改行は残すので行番号がずれない。 */
        private void blank(int from, int to) {
            for (int i = from; i < to && i < this.out.length; i++) {
                if (this.out[i] != '\n' && this.out[i] != '\r') {
                    this.out[i] = ' ';
                }
            }
        }

        private void blank(Token t) {
            this.blank(t.start, t.end);
        }

        private void insertAt(int pos, String text) {
            this.inserts.add(new int[]{pos});
            this.insertTexts.add(text);
        }

        String run() {
            for (int i = 0; i < this.tokens.size(); i++) {
                Token t = this.tokens.get(i);
                if (t.type == TokenType.PUNCT) {
                    if (t.is("{")) {
                        this.depth++;
                    } else if (t.is("}")) {
                        this.depth--;
                        if (!this.classBodyDepths.isEmpty()
                                && this.classBodyDepths.get(this.classBodyDepths.size() - 1) == this.depth) {
                            this.classBodyDepths.remove(this.classBodyDepths.size() - 1);
                        }
                    }
                }
                i = this.handle(i);
            }
            return this.assemble();
        }

        private String assemble() {
            if (this.inserts.isEmpty()) {
                return new String(this.out);
            }
            //挿し込み位置の昇順で組み立てる
            Integer[] order = new Integer[this.inserts.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(order, (a, b) -> Integer.compare(this.inserts.get(a)[0], this.inserts.get(b)[0]));
            StringBuilder sb = new StringBuilder(this.out.length + 256);
            int cursor = 0;
            for (int idx : order) {
                int at = this.inserts.get(idx)[0];
                sb.append(this.out, cursor, Math.max(0, at - cursor));
                sb.append(this.insertTexts.get(idx));
                cursor = at;
            }
            sb.append(this.out, cursor, this.out.length - cursor);
            return sb.toString();
        }

        /** 1 トークンぶん処理する。読み飛ばした場合は最後に見た添字を返す。 */
        private int handle(int i) {
            Token t = this.tokens.get(i);
            if (t.type == TokenType.WORD) {
                return this.handleWord(i);
            }
            if (t.type == TokenType.PUNCT) {
                return this.handlePunct(i);
            }
            return i;
        }

        private int handleWord(int i) {
            Token t = this.tokens.get(i);
            //--- 丸ごと消す宣言 ---
            if ((t.is("interface") || t.is("declare")) && isStatementStart(this.tokens, i)
                    && next(this.tokens, i) != null) {
                return this.blankDeclaration(i);
            }
            if (t.is("type") && isStatementStart(this.tokens, i)) {
                //type X = ... ; (プロパティ名の "type" と区別するため = があるかを見る)
                int n = nextIndex(this.tokens, i);
                if (n >= 0 && this.tokens.get(n).type == TokenType.WORD) {
                    int a = nextIndex(this.tokens, n);
                    //ジェネリクスを飛ばす
                    if (a >= 0 && this.tokens.get(a).is("<")) {
                        int close = this.matchAngle(a);
                        a = close < 0 ? a : nextIndex(this.tokens, close);
                    }
                    if (a >= 0 && this.tokens.get(a).is("=")) {
                        return this.blankDeclaration(i);
                    }
                }
                return i;
            }
            if (t.is("import") && isStatementStart(this.tokens, i)) {
                Token n = next(this.tokens, i);
                if (n != null && n.is("type")) {
                    //★import type は { } の後に from "..." が続く。宣言の汎用処理は
                    //  波括弧で切り上げてしまうので、ここは行末/セミコロンまで消す。
                    return this.blankToLineEnd(i);
                }
                return i;
            }
            //export は消すだけ。パックスクリプトは 1 グローバル空間なので、
            //消せば「普通のグローバル宣言」になって従来どおり動く。
            if (t.is("export") && isStatementStart(this.tokens, i)) {
                Token n = next(this.tokens, i);
                if (n != null && n.is("type")) {
                    return this.blankDeclaration(i);
                }
                this.blank(t);
                return i;
            }
            //--- enum ---
            if (t.is("enum") && (isStatementStart(this.tokens, i)
                    || (prev(this.tokens, i) != null && prev(this.tokens, i).is("const")))) {
                return this.rewriteEnum(i);
            }
            //--- class ---
            if (t.is("class")) {
                return this.handleClass(i);
            }
            //--- as / satisfies ---
            if ((t.is("as") || t.is("satisfies")) && prev(this.tokens, i) != null) {
                Token p = prev(this.tokens, i);
                boolean valueBefore = p.type == TokenType.WORD || p.type == TokenType.STRING
                    || p.type == TokenType.NUMBER || p.type == TokenType.TEMPLATE
                    || p.is(")") || p.is("]") || p.is("}");
                if (valueBefore) {
                    int n = nextIndex(this.tokens, i);
                    if (n >= 0 && !this.tokens.get(n).is("(")) {
                        int end = this.scanType(n, false);
                        if (end > n) {
                            this.blank(t.start, this.tokens.get(end - 1).end);
                            return end - 1;
                        }
                    }
                }
                return i;
            }
            //--- 変数宣言の型注釈 ---
            if (t.is("var") || t.is("let") || t.is("const")) {
                return this.handleVarDeclaration(i);
            }
            //--- function 宣言のジェネリクスと戻り値 ---
            if (t.is("function")) {
                return this.handleFunction(i);
            }
            return i;
        }

        private int handlePunct(int i) {
            Token t = this.tokens.get(i);
            //--- 非 null 表明 x! ---
            if (t.is("!")) {
                Token p = prev(this.tokens, i);
                Token n = next(this.tokens, i);
                boolean afterValue = p != null && (p.type == TokenType.WORD || p.type == TokenType.STRING
                    || p.type == TokenType.NUMBER || p.is(")") || p.is("]"));
                boolean beforeOperator = n != null && (n.is(".") || n.is(";") || n.is(",") || n.is(")")
                    || n.is("]") || n.is("}") || n.is("(") || n.is("[") || n.is(":") || n.is("="));
                if (afterValue && beforeOperator) {
                    //a! : T (definite assignment) も a!.b も同じく ! を消すだけでよい
                    this.blank(t);
                }
                return i;
            }
            //--- デコレータ @Foo(...) ---
            if (t.is("@")) {
                Token n = next(this.tokens, i);
                if (n != null && n.type == TokenType.WORD) {
                    int j = nextIndex(this.tokens, nextIndex(this.tokens, i));
                    int end = this.tokens.get(nextIndex(this.tokens, i)).end;
                    if (j >= 0 && this.tokens.get(j).is("(")) {
                        int close = this.matchPair(j, "(", ")");
                        if (close >= 0) {
                            end = this.tokens.get(close).end;
                            this.blank(t.start, end);
                            return close;
                        }
                    }
                    this.blank(t.start, end);
                    return nextIndex(this.tokens, i);
                }
                return i;
            }
            //--- 呼び出し側ジェネリクス f<T>(...) ---
            if (t.is("<")) {
                Token p = prev(this.tokens, i);
                if (p != null && (p.type == TokenType.WORD || p.is(")") || p.is("]"))) {
                    int close = this.matchAngle(i);
                    if (close > 0) {
                        Token after = next(this.tokens, close);
                        if (after != null && (after.is("(") || after.is("`"))) {
                            this.blank(t.start, this.tokens.get(close).end);
                            return close;
                        }
                    }
                }
                return i;
            }
            //--- 引数リスト・関数の戻り値 ---
            if (t.is("(")) {
                return this.handleParenGroup(i);
            }
            return i;
        }

        /**
         * 括弧グループ。引数リストなら中の型注釈を消し、閉じ括弧の後の {@code : 型} も消す。
         *
         * <p>普通の式の括弧 {@code (a + b)} でも中に型注釈は現れないので、
         * 「識別子の直後に {@code :} が来たら注釈」という判定で誤爆しない。
         * ただし三項演算子と衝突するため、<b>引数リストらしさ</b>を確かめてから処理する。
         */
        private int handleParenGroup(int i) {
            int close = this.matchPair(i, "(", ")");
            if (close < 0) {
                return i;
            }
            if (this.looksLikeParameterList(i, close)) {
                this.stripParameterTypes(i, close);
            }
            //戻り値の型: ) : 型 の後に { か => が来る
            int n = nextIndex(this.tokens, close);
            if (n >= 0 && this.tokens.get(n).is(":")) {
                int end = this.scanType(nextIndex(this.tokens, n), true);
                if (end > n) {
                    Token after = end < this.tokens.size() ? this.tokens.get(end) : null;
                    int a = end;
                    while (a < this.tokens.size() && (this.tokens.get(a).type == TokenType.WS
                            || this.tokens.get(a).type == TokenType.COMMENT)) {
                        a++;
                    }
                    after = a < this.tokens.size() ? this.tokens.get(a) : null;
                    if (after != null && (after.is("{") || after.is("=>") || after.is(";"))) {
                        this.blank(this.tokens.get(n).start, this.tokens.get(end - 1).end);
                    }
                }
            }
            return i;
        }

        /**
         * 引数リストか。
         *
         * <p><b>「閉じ括弧の後に {@code {} か {@code =>} が来るか」で判定する。</b>
         * 中に {@code 識別子 :} があるかで見ていたときは、三項演算子を巻き込んで壊した:
         * <pre>
         *   (entity.seatRotation &lt; 0 ? lightF : lightB).render(renderer)
         *                                        ~~~~~~~~ 型注釈と誤認して消していた
         * </pre>
         * 関数の引数リストなら必ず本体 {@code {} かアロー {@code =>} が続く。
         * {@code if (a ? b : c) {} も本体が続くので、制御構文のキーワードだけ先に除く。
         */
        private boolean looksLikeParameterList(int open, int close) {
            Token p = prev(this.tokens, open);
            if (p != null && p.type == TokenType.WORD
                    && (p.is("if") || p.is("while") || p.is("for") || p.is("switch")
                        || p.is("catch") || p.is("with") || p.is("return") || p.is("typeof")
                        || p.is("case") || p.is("in") || p.is("of") || p.is("new")
                        || p.is("delete") || p.is("void") || p.is("throw"))) {
                return false;
            }
            int n = nextIndex(this.tokens, close);
            if (n < 0) {
                return false;
            }
            //戻り値の型注釈を読み飛ばす
            if (this.tokens.get(n).is(":")) {
                int e = this.scanType(nextIndex(this.tokens, n), true);
                while (e < this.tokens.size() && (this.tokens.get(e).type == TokenType.WS
                        || this.tokens.get(e).type == TokenType.COMMENT)) {
                    e++;
                }
                n = e;
            }
            if (n < 0 || n >= this.tokens.size()) {
                return false;
            }
            Token after = this.tokens.get(n);
            return after.is("{") || after.is("=>");
        }

        /** 引数リストの中の {@code ?} と {@code : 型} と修飾子を消す。 */
        private void stripParameterTypes(int open, int close) {
            int localDepth = 0;
            List<String> paramProps = new ArrayList<>();
            for (int j = open + 1; j < close; j++) {
                Token t = this.tokens.get(j);
                if (t.type == TokenType.PUNCT) {
                    if (t.is("(") || t.is("[") || t.is("{")) {
                        localDepth++;
                        continue;
                    }
                    if (t.is(")") || t.is("]") || t.is("}")) {
                        localDepth--;
                        continue;
                    }
                    if (localDepth != 0) {
                        continue;
                    }
                    if (t.is("?")) {
                        Token p = prev(this.tokens, j);
                        if (p != null && p.type == TokenType.WORD) {
                            this.blank(t);
                        }
                        continue;
                    }
                    if (t.is(":")) {
                        Token p = prev(this.tokens, j);
                        if (p == null || !(p.type == TokenType.WORD || p.is("]") || p.is("?")
                                || p.is(")"))) {
                            continue;
                        }
                        int end = this.scanType(nextIndex(this.tokens, j), true);
                        if (end > j) {
                            this.blank(t.start, this.tokens.get(end - 1).end);
                            j = end - 1;
                        }
                    }
                    continue;
                }
                if (localDepth != 0 || t.type != TokenType.WORD) {
                    continue;
                }
                //引数プロパティ: constructor(private a: T) → this.a = a を足す
                if (t.is("public") || t.is("private") || t.is("protected") || t.is("readonly")
                        || t.is("override")) {
                    Token n = next(this.tokens, j);
                    if (n != null && (n.type == TokenType.WORD || n.is("readonly"))) {
                        this.blank(t);
                        int nameIdx = nextIndex(this.tokens, j);
                        while (nameIdx >= 0 && this.tokens.get(nameIdx).type == TokenType.WORD
                                && (this.tokens.get(nameIdx).is("readonly"))) {
                            this.blank(this.tokens.get(nameIdx));
                            nameIdx = nextIndex(this.tokens, nameIdx);
                        }
                        if (nameIdx >= 0 && this.tokens.get(nameIdx).type == TokenType.WORD) {
                            paramProps.add(this.tokens.get(nameIdx).text);
                        }
                    }
                }
            }
            if (!paramProps.isEmpty()) {
                //コンストラクタ本体の先頭へ代入を差し込む
                int brace = nextIndex(this.tokens, close);
                if (brace >= 0 && this.tokens.get(brace).is("{")) {
                    StringBuilder sb = new StringBuilder();
                    for (String p : paramProps) {
                        sb.append(" this.").append(p).append(" = ").append(p).append(';');
                    }
                    this.insertAt(this.tokens.get(brace).end, sb.toString());
                }
            }
        }

        /** {@code var x: T = ...} の型注釈を消す。 */
        private int handleVarDeclaration(int i) {
            int j = nextIndex(this.tokens, i);
            while (j >= 0) {
                Token t = this.tokens.get(j);
                if (t.type == TokenType.WORD) {
                    int n = nextIndex(this.tokens, j);
                    if (n >= 0 && this.tokens.get(n).is(":")) {
                        int end = this.scanType(nextIndex(this.tokens, n), true);
                        if (end > n) {
                            this.blank(this.tokens.get(n).start, this.tokens.get(end - 1).end);
                            j = end;
                            continue;
                        }
                    }
                }
                if (t.is(",")) {
                    j = nextIndex(this.tokens, j);
                    continue;
                }
                if (t.is(";") || t.is("=") || t.type == TokenType.WS) {
                    break;
                }
                break;
            }
            return i;
        }

        /** {@code function f<T>(...)} のジェネリクスを消す。引数と戻り値は ( の側で処理される。 */
        private int handleFunction(int i) {
            int j = nextIndex(this.tokens, i);
            if (j >= 0 && this.tokens.get(j).type == TokenType.WORD) {
                j = nextIndex(this.tokens, j);
            }
            if (j >= 0 && this.tokens.get(j).is("<")) {
                int close = this.matchAngle(j);
                if (close > 0) {
                    this.blank(this.tokens.get(j).start, this.tokens.get(close).end);
                    return close;
                }
            }
            return i;
        }

        /** クラス宣言。ジェネリクス・{@code implements} 節・本体の修飾子とプロパティ型を消す。 */
        private int handleClass(int i) {
            int j = nextIndex(this.tokens, i);
            String className = null;
            if (j >= 0 && this.tokens.get(j).type == TokenType.WORD) {
                className = this.tokens.get(j).text;
                j = nextIndex(this.tokens, j);
            }
            boolean hasExtends = false;
            if (j >= 0 && this.tokens.get(j).is("<")) {
                int close = this.matchAngle(j);
                if (close > 0) {
                    this.blank(this.tokens.get(j).start, this.tokens.get(close).end);
                    j = nextIndex(this.tokens, close);
                }
            }
            //extends Base<T> の型引数、implements 節
            while (j >= 0) {
                Token t = this.tokens.get(j);
                if (t.is("{")) {
                    this.classBodyDepths.add(this.depth);
                    this.stripClassBody(j, className, hasExtends);
                    break;
                }
                if (t.is("extends")) {
                    hasExtends = true;
                }
                if (t.is("implements")) {
                    int brace = j;
                    while (brace < this.tokens.size() && !this.tokens.get(brace).is("{")) {
                        brace++;
                    }
                    if (brace < this.tokens.size()) {
                        this.blank(t.start, this.tokens.get(brace).start);
                        j = brace;
                        continue;
                    }
                }
                if (t.is("<")) {
                    int close = this.matchAngle(j);
                    if (close > 0) {
                        this.blank(t.start, this.tokens.get(close).end);
                        j = nextIndex(this.tokens, close);
                        continue;
                    }
                }
                j = nextIndex(this.tokens, j);
            }
            return i;
        }

        /**
         * クラス本体を 1 メンバずつ処理する。
         *
         * <p><b>フィールド宣言は消さないといけない。</b> Nashorn は ES6 までしか解釈できず、
         * クラスフィールド ({@code name;} や {@code x = 1;}) は ES2022 の構文なので構文エラーになる。
         * TypeScript 自身も「初期化子の無いフィールド宣言」は型情報だけなので出力から消す。
         * 初期化子つきはコンストラクタの先頭へ移す (tsc の {@code useDefineForClassFields: false} と同じ)。
         */
        private void stripClassBody(int openBrace, String className, boolean hasExtends) {
            int close = this.matchPair(openBrace, "{", "}");
            if (close < 0) {
                return;
            }
            List<String> instanceInits = new ArrayList<>();
            List<String> staticInits = new ArrayList<>();
            int ctorBodyBrace = -1;

            int j = nextIndex2(openBrace);
            while (j >= 0 && j < close) {
                Token t = this.tokens.get(j);
                if (t.is(";")) {
                    j = nextIndex2(j);
                    continue;
                }
                //--- 修飾子を読み飛ばす ---
                boolean isStatic = false;
                while (j >= 0 && j < close && this.tokens.get(j).type == TokenType.WORD
                        && isMemberModifier(this.tokens.get(j).text)) {
                    Token n = next(this.tokens, j);
                    if (n == null || !(n.type == TokenType.WORD || n.is("[")
                            || n.type == TokenType.STRING || n.is("*") || n.is("#"))) {
                        break;  //modifier という名前のメンバ
                    }
                    if (this.tokens.get(j).is("static")) {
                        isStatic = true;
                    } else {
                        this.blank(this.tokens.get(j));   //static は JS にもあるので残す
                    }
                    j = nextIndex2(j);
                }
                if (j < 0 || j >= close) {
                    break;
                }
                //--- get / set / async / * ---
                Token cur = this.tokens.get(j);
                if (cur.type == TokenType.WORD && (cur.is("get") || cur.is("set") || cur.is("async"))) {
                    Token n = next(this.tokens, j);
                    if (n != null && (n.type == TokenType.WORD || n.type == TokenType.STRING)) {
                        j = nextIndex2(j);
                    }
                }
                if (j < 0 || j >= close) {
                    break;
                }
                //--- メンバ名 ---
                int nameIdx = j;
                Token nameTok = this.tokens.get(nameIdx);
                String memberName = nameTok.type == TokenType.STRING
                    ? nameTok.text.substring(1, nameTok.text.length() - 1) : nameTok.text;
                int after = nextIndex2(nameIdx);
                if (after >= 0 && this.tokens.get(after).is("[")) {
                    int b = this.matchPair(after, "[", "]");
                    after = b < 0 ? after : nextIndex2(b);
                }
                //--- ジェネリクス ---
                if (after >= 0 && this.tokens.get(after).is("<")) {
                    int a = this.matchAngle(after);
                    if (a > 0) {
                        this.blank(this.tokens.get(after).start, this.tokens.get(a).end);
                        after = nextIndex2(a);
                    }
                }
                if (after < 0 || after >= close) {
                    break;
                }
                //--- メソッド ---
                if (this.tokens.get(after).is("(")) {
                    int paren = this.matchPair(after, "(", ")");
                    if (paren < 0) {
                        break;
                    }
                    boolean isCtor = "constructor".equals(memberName);
                    int body = nextIndex2(paren);
                    if (body >= 0 && this.tokens.get(body).is(":")) {
                        int e = this.scanType(nextIndex2(body), true);
                        if (e > body) {
                            this.blank(this.tokens.get(body).start, this.tokens.get(e - 1).end);
                        }
                        body = e;
                        while (body < this.tokens.size() && (this.tokens.get(body).type == TokenType.WS
                                || this.tokens.get(body).type == TokenType.COMMENT)) {
                            body++;
                        }
                    }
                    if (body >= 0 && body < this.tokens.size() && this.tokens.get(body).is("{")) {
                        if (isCtor) {
                            ctorBodyBrace = body;
                        }
                        int bodyEnd = this.matchPair(body, "{", "}");
                        j = bodyEnd < 0 ? close : nextIndex2(bodyEnd);
                    } else {
                        //本体なし = オーバーロード宣言。丸ごと消す
                        this.blank(nameTok.start, this.tokens.get(paren).end);
                        j = nextIndex2(paren);
                    }
                    continue;
                }
                //--- フィールド宣言 ---
                int fieldEnd = after;
                String init = null;
                if (this.tokens.get(fieldEnd).is("?") || this.tokens.get(fieldEnd).is("!")) {
                    fieldEnd = nextIndex2(fieldEnd);
                }
                if (fieldEnd >= 0 && fieldEnd < close && this.tokens.get(fieldEnd).is(":")) {
                    fieldEnd = this.scanType(nextIndex2(fieldEnd), true);
                    while (fieldEnd < this.tokens.size()
                            && (this.tokens.get(fieldEnd).type == TokenType.WS
                                || this.tokens.get(fieldEnd).type == TokenType.COMMENT)) {
                        fieldEnd++;
                    }
                }
                if (fieldEnd >= 0 && fieldEnd < close && this.tokens.get(fieldEnd).is("=")) {
                    int k = nextIndex2(fieldEnd);
                    int d = 0;
                    int from = k;
                    while (k < close) {
                        Token v = this.tokens.get(k);
                        if (v.type == TokenType.PUNCT) {
                            if (v.is("(") || v.is("[") || v.is("{")) {
                                d++;
                            } else if (v.is(")") || v.is("]") || v.is("}")) {
                                d--;
                            } else if (d == 0 && v.is(";")) {
                                break;
                            }
                        }
                        if (d == 0 && v.type == TokenType.WS && v.text.indexOf('\n') >= 0) {
                            int nx = nextIndex(this.tokens, k - 1);
                            if (nx >= 0 && nx < close) {
                                Token nt = this.tokens.get(nx);
                                if (nt.type == TokenType.WORD || nt.is("}")) {
                                    break;
                                }
                            }
                        }
                        k++;
                    }
                    init = new String(this.out, this.tokens.get(from).start,
                        this.tokens.get(Math.max(from, k - 1)).end - this.tokens.get(from).start).trim();
                    fieldEnd = k;
                }
                int stop = fieldEnd;
                if (stop < close && this.tokens.get(stop).is(";")) {
                    stop++;
                }
                this.blank(nameTok.start, this.tokens.get(Math.max(0, Math.min(stop, close)) - 1).end);
                if (init != null && !init.isEmpty()) {
                    if (isStatic) {
                        staticInits.add(memberName + " = " + init);
                    } else {
                        instanceInits.add("this." + memberName + " = " + init + ";");
                    }
                }
                j = stop >= close ? -1 : nextIndex2(stop - 1);
            }

            //初期化子つきフィールドはコンストラクタの先頭へ移す
            if (!instanceInits.isEmpty() && ctorBodyBrace >= 0) {
                StringBuilder sb = new StringBuilder();
                for (String s : instanceInits) {
                    sb.append(' ').append(s);
                }
                this.insertAt(this.tokens.get(ctorBodyBrace).end, sb.toString());
            } else if (!instanceInits.isEmpty() && !hasExtends) {
                StringBuilder sb = new StringBuilder(" constructor() {");
                for (String s : instanceInits) {
                    sb.append(' ').append(s);
                }
                sb.append(" }");
                this.insertAt(this.tokens.get(openBrace).end, sb.toString());
            }
            //静的フィールドはクラスの外へ (Nashorn は static フィールドを解釈できない)
            if (!staticInits.isEmpty() && className != null) {
                StringBuilder sb = new StringBuilder();
                for (String s : staticInits) {
                    sb.append(' ').append(className).append('.').append(s).append(';');
                }
                this.insertAt(this.tokens.get(close).end, sb.toString());
            }
        }

        /** 空白・コメントを飛ばした次の添字。 */
        private int nextIndex2(int i) {
            return nextIndex(this.tokens, i);
        }

        /** {@code enum E { A, B = 2 }} を TypeScript と同じ形へ展開する。 */
        private int rewriteEnum(int i) {
            int nameIdx = nextIndex(this.tokens, i);
            if (nameIdx < 0 || this.tokens.get(nameIdx).type != TokenType.WORD) {
                return i;
            }
            String name = this.tokens.get(nameIdx).text;
            int brace = nextIndex(this.tokens, nameIdx);
            if (brace < 0 || !this.tokens.get(brace).is("{")) {
                return i;
            }
            int close = this.matchPair(brace, "{", "}");
            if (close < 0) {
                return i;
            }
            StringBuilder body = new StringBuilder();
            int auto = 0;
            int j = brace + 1;
            while (j < close) {
                Token t = this.tokens.get(j);
                if (t.type == TokenType.WS || t.type == TokenType.COMMENT || t.is(",")) {
                    j++;
                    continue;
                }
                String member;
                if (t.type == TokenType.WORD) {
                    member = t.text;
                } else if (t.type == TokenType.STRING) {
                    member = t.text.substring(1, t.text.length() - 1);
                } else {
                    j++;
                    continue;
                }
                int eq = nextIndex(this.tokens, j);
                if (eq >= 0 && eq < close && this.tokens.get(eq).is("=")) {
                    //値つき。次の , か } まで
                    int valueStart = nextIndex(this.tokens, eq);
                    int k = valueStart;
                    int d = 0;
                    StringBuilder value = new StringBuilder();
                    while (k < close) {
                        Token v = this.tokens.get(k);
                        if (v.type == TokenType.PUNCT) {
                            if (v.is("(") || v.is("[") || v.is("{")) {
                                d++;
                            } else if (v.is(")") || v.is("]") || v.is("}")) {
                                d--;
                            } else if (d == 0 && v.is(",")) {
                                break;
                            }
                        }
                        value.append(v.text);
                        k++;
                    }
                    String v = value.toString().trim();
                    boolean numeric = v.matches("-?\\d+");
                    if (numeric) {
                        auto = Integer.parseInt(v) + 1;
                        body.append(name).append('[').append(name).append("[\"").append(member)
                            .append("\"] = ").append(v).append("] = \"").append(member).append("\"; ");
                    } else {
                        body.append(name).append("[\"").append(member).append("\"] = ")
                            .append(v).append("; ");
                    }
                    j = k;
                } else {
                    body.append(name).append('[').append(name).append("[\"").append(member)
                        .append("\"] = ").append(auto).append("] = \"").append(member).append("\"; ");
                    auto++;
                    j = nextIndex(this.tokens, j);
                    if (j < 0) {
                        break;
                    }
                }
            }
            //const enum の const も消す
            int constIdx = prevIndex(this.tokens, i);
            int start = this.tokens.get(i).start;
            if (constIdx >= 0 && this.tokens.get(constIdx).is("const")) {
                start = this.tokens.get(constIdx).start;
            }
            this.blank(start, this.tokens.get(close).end);
            this.insertAt(start, "var " + name + "; (function (" + name + ") { " + body
                + "})(" + name + " || (" + name + " = {}));");
            return close;
        }

        /** セミコロンか行末まで空白にする ({@code import type ... from "..."} 用)。 */
        private int blankToLineEnd(int i) {
            int j = i;
            for (int k = i + 1; k < this.tokens.size(); k++) {
                Token t = this.tokens.get(k);
                if (t.type == TokenType.PUNCT && t.is(";")) {
                    j = k;
                    break;
                }
                if (t.type == TokenType.WS && t.text.indexOf('\n') >= 0) {
                    j = k - 1;
                    break;
                }
                j = k;
            }
            this.blank(this.tokens.get(i).start, this.tokens.get(Math.min(j, this.tokens.size() - 1)).end);
            return j;
        }

        /** {@code interface} / {@code declare} / {@code type X = ...} を丸ごと空白にする。 */
        private int blankDeclaration(int i) {
            int j = i;
            //本体 { } を持つならその末尾まで、無ければ ; か行末まで
            int limit = this.tokens.size();
            int braceStart = -1;
            for (int k = i + 1; k < limit; k++) {
                Token t = this.tokens.get(k);
                if (t.type == TokenType.WS && t.text.indexOf('\n') >= 0 && braceStart < 0) {
                    //改行で終わる宣言 (type X = number)
                    int p = prevIndex(this.tokens, k);
                    if (p >= 0 && !this.tokens.get(p).is("=") && !this.tokens.get(p).is("|")
                            && !this.tokens.get(p).is("&") && !this.tokens.get(p).is(",")
                            && !this.tokens.get(p).is("<") && !this.tokens.get(p).is("extends")) {
                        j = k;
                        break;
                    }
                    continue;
                }
                if (t.is("{")) {
                    int close = this.matchPair(k, "{", "}");
                    if (close < 0) {
                        return i;
                    }
                    braceStart = k;
                    j = close + 1;
                    Token after = next(this.tokens, close);
                    if (after != null && after.is(";")) {
                        j = nextIndex(this.tokens, close) + 1;
                    }
                    break;
                }
                if (t.is(";")) {
                    j = k + 1;
                    break;
                }
                j = k + 1;
            }
            int end = j > 0 && j - 1 < this.tokens.size()
                ? this.tokens.get(Math.min(j - 1, this.tokens.size() - 1)).end
                : this.out.length;
            this.blank(this.tokens.get(i).start, end);
            return Math.max(i, j - 1);
        }

        /**
         * 型を 1 つ読み飛ばす。戻り値は型の<b>次</b>のトークン添字。
         *
         * @param i           型の先頭
         * @param stopAtArrow 後方互換のため残しているが未使用。{@code =>} を型の一部と見なすかは
         *                    「直前が丸括弧グループだったか」で決まる。こうすると
         *                    {@code (a: number): boolean => ...} のアロー関数と
         *                    {@code const g: (x: number) => string} の関数型を取り違えない
         */
        private int scanType(int i, boolean stopAtArrow) {
            if (i < 0 || i >= this.tokens.size()) {
                return i;
            }
            int j = i;
            boolean expectAtom = true;
            boolean lastWasParen = false;
            int guard = 0;
            while (j < this.tokens.size() && guard++ < 4096) {
                Token t = this.tokens.get(j);
                if (t.type == TokenType.WS || t.type == TokenType.COMMENT) {
                    j++;
                    continue;
                }
                if (expectAtom) {
                    if (t.type == TokenType.WORD || t.type == TokenType.STRING
                            || t.type == TokenType.NUMBER) {
                        //keyof / typeof / readonly / infer / new は前置なので atom を継続
                        if (t.is("keyof") || t.is("typeof") || t.is("readonly")
                                || t.is("infer") || t.is("new") || t.is("abstract")) {
                            j++;
                            continue;
                        }
                        j++;
                        expectAtom = false;
                        lastWasParen = false;
                        continue;
                    }
                    if (t.is("(")) {
                        int close = this.matchPair(j, "(", ")");
                        if (close < 0) {
                            return j;
                        }
                        j = close + 1;
                        expectAtom = false;
                        lastWasParen = true;
                        continue;
                    }
                    if (t.is("{")) {
                        int close = this.matchPair(j, "{", "}");
                        if (close < 0) {
                            return j;
                        }
                        j = close + 1;
                        expectAtom = false;
                        lastWasParen = false;
                        continue;
                    }
                    if (t.is("[")) {
                        int close = this.matchPair(j, "[", "]");
                        if (close < 0) {
                            return j;
                        }
                        j = close + 1;
                        expectAtom = false;
                        lastWasParen = false;
                        continue;
                    }
                    if (t.is("|") || t.is("&")) {
                        //先頭の | は許される (union の書き始め)
                        j++;
                        continue;
                    }
                    if (t.is("-")) {
                        j++;
                        continue;
                    }
                    return j;
                }
                //中置の位置
                if (t.is("|") || t.is("&") || t.is(".") || t.is("extends")) {
                    j++;
                    expectAtom = true;
                    lastWasParen = false;
                    continue;
                }
                if (t.is("[")) {
                    int close = this.matchPair(j, "[", "]");
                    if (close < 0) {
                        return j;
                    }
                    j = close + 1;
                    continue;
                }
                if (t.is("<")) {
                    int close = this.matchAngle(j);
                    if (close < 0) {
                        return j;
                    }
                    j = close + 1;
                    continue;
                }
                if (t.is("=>") && lastWasParen) {
                    j++;
                    expectAtom = true;
                    continue;
                }
                if (t.is("?")) {
                    //条件型 A extends B ? C : D
                    j++;
                    expectAtom = true;
                    continue;
                }
                return j;
            }
            return j;
        }

        /** 対応する閉じ括弧の添字。 */
        private int matchPair(int i, String open, String close) {
            int depth = 0;
            for (int j = i; j < this.tokens.size(); j++) {
                Token t = this.tokens.get(j);
                if (t.type != TokenType.PUNCT) {
                    continue;
                }
                if (t.is(open)) {
                    depth++;
                } else if (t.is(close)) {
                    depth--;
                    if (depth == 0) {
                        return j;
                    }
                }
            }
            return -1;
        }

        /**
         * {@code <} に対応する {@code >} を探す。型に出てこないトークンが現れたら
         * ジェネリクスではないと判断して -1 を返す (比較演算子との取り違え防止)。
         */
        private int matchAngle(int i) {
            int depth = 0;
            for (int j = i; j < this.tokens.size(); j++) {
                Token t = this.tokens.get(j);
                if (t.type == TokenType.WS || t.type == TokenType.COMMENT
                        || t.type == TokenType.WORD || t.type == TokenType.STRING
                        || t.type == TokenType.NUMBER) {
                    continue;
                }
                if (t.type != TokenType.PUNCT) {
                    return -1;
                }
                if (t.is("<")) {
                    depth++;
                } else if (t.is(">")) {
                    depth--;
                    if (depth == 0) {
                        return j;
                    }
                } else if (t.is(">>")) {
                    depth -= 2;
                    if (depth <= 0) {
                        return j;
                    }
                } else if (!(t.is(",") || t.is(".") || t.is("[") || t.is("]") || t.is("(")
                        || t.is(")") || t.is("{") || t.is("}") || t.is("|") || t.is("&")
                        || t.is("=>") || t.is("?") || t.is(":") || t.is("=") || t.is("..."))) {
                    return -1;
                }
            }
            return -1;
        }
    }
}
