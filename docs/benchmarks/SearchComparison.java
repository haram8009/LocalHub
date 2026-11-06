import java.sql.*;
import java.util.*;

/** Isolated benchmark: never connects to the application database. JDK 21 + H2 2.3.232. */
public class SearchComparison {
    record Hit(long id, int score) {}
    static final Comparator<Hit> ORDER = Comparator.comparingInt(Hit::score).reversed()
        .thenComparing(Comparator.comparingLong(Hit::id).reversed());
    static Connection db;
    static boolean contains;
    static List<Hit> like(String[] terms, boolean and) throws Exception {
        var conditions=new ArrayList<String>(); var points=new ArrayList<String>();
        for(String term:terms) {
            String pattern = contains ? "%"+term+"%" : "% "+term+" %";
            String t="title like '"+pattern+"'", b="body like '"+pattern+"'";
            conditions.add("("+t+" or "+b+")");
            points.add("case when "+t+" then 2 else 0 end + case when "+b+" then 1 else 0 end");
        }
        return query("select id, "+String.join(" + ",points)+" score from docs where "+String.join(and?" and ":" or ",conditions)+" order by score desc,id desc");
    }
    static List<Hit> sqlIndex(String[] terms,boolean and) throws Exception {
        if (contains) {
            var conditions = new ArrayList<String>(); var points = new ArrayList<String>(); var all = new ArrayList<String>();
            for (String term : terms) {
                String match = "term like '%"+term+"%'";
                conditions.add(match);
                points.add("max(case when "+match+" and title_hit=1 then 2 else 0 end) + max(case when "+match+" and body_hit=1 then 1 else 0 end)");
                all.add("max(case when "+match+" then 1 else 0 end)=1");
            }
            return query("select post_id, "+String.join(" + ",points)+" score from terms where "+String.join(" or ",conditions)+" group by post_id"+(and?" having "+String.join(" and ",all):"")+" order by score desc,post_id desc");
        }
        String in="'"+String.join("','",terms)+"'";
        return query("select post_id, sum(title_hit*2+body_hit) score from terms where term in ("+in+") group by post_id"+(and?" having count(*)="+terms.length:"")+" order by score desc,post_id desc");
    }
    static List<Hit> query(String sql) throws Exception {
        var hits=new ArrayList<Hit>();
        try(var st=db.createStatement();var rs=st.executeQuery(sql)) { while(rs.next()) hits.add(new Hit(rs.getLong(1),rs.getInt(2))); }
        return hits;
    }
    static List<Hit> javaIndex(String[] terms,boolean and) throws Exception {
        var sets=new HashMap<String,Set<Long>>(); var scores=new HashMap<Long,Integer>();
        Map<String,Map<Long,Integer>> flags = new HashMap<>();
        for(String term:terms) flags.put(term,new HashMap<>());
        for(String term:terms) sets.put(term,new HashSet<>());
        String where = contains ? String.join(" or ", Arrays.stream(terms).map(t -> "term like '%"+t+"%'").toList()) : "term in ('"+String.join("','",terms)+"')";
        try(var st=db.createStatement();var rs=st.executeQuery("select term,post_id,title_hit,body_hit from terms where "+where)) {
            while(rs.next()) {
                long id=rs.getLong(2); String word=rs.getString(1); int bits=rs.getInt(3)*2+rs.getInt(4);
                for(String term:terms) if(contains ? word.contains(term) : word.equals(term)) {
                    sets.get(term).add(id); flags.get(term).merge(id,bits,(left,right)->left|right);
                }
            }
        }
        for(var termFlags:flags.values()) termFlags.forEach((id,points)->scores.merge(id,points,Integer::sum));
        var lists=new ArrayList<>(sets.values()); lists.sort(Comparator.comparingInt(Set::size));
        Set<Long> candidates=new HashSet<>();
        if(and) { candidates.addAll(lists.get(0)); for(int i=1;i<lists.size();i++) candidates.retainAll(lists.get(i)); }
        else for(var set:lists) candidates.addAll(set);
        return candidates.stream().map(id->new Hit(id,scores.get(id))).sorted(ORDER).toList();
    }
    static void seed(int count) throws Exception {
        try(var st=db.createStatement()) {
            st.execute("create table docs(id bigint primary key,title varchar(4000),body varchar(4000))");
            st.execute("create table terms(term varchar(100),post_id bigint,title_hit int,body_hit int,primary key(term,post_id))");
        }
        Random random=new Random(20261015);
        db.setAutoCommit(false);
        try(var post=db.prepareStatement("insert into docs values(?,?,?)");var term=db.prepareStatement("insert into terms values(?,?,?,?)")) {
            for(long id=1;id<=count;id++) {
                var title=new LinkedHashSet<String>(); var body=new LinkedHashSet<String>();
                title.add("여행"); if(id%2==0) body.add("코스");
                for(int i=0;i<4;i++) title.add("단어"+random.nextInt(200));
                for(int i=0;i<16;i++) body.add("단어"+random.nextInt(200));
                post.setLong(1,id);post.setString(2," "+String.join(" ",title)+" ");post.setString(3," "+String.join(" ",body)+" ");post.addBatch();
                var all=new HashSet<>(title);all.addAll(body);
                for(String word:all) { term.setString(1,word);term.setLong(2,id);term.setInt(3,title.contains(word)?1:0);term.setInt(4,body.contains(word)?1:0);term.addBatch(); }
                if(id%500==0) { post.executeBatch();term.executeBatch(); }
            }
            post.executeBatch();term.executeBatch();db.commit();
        }
        db.setAutoCommit(true);
    }
    interface Run { List<Hit> run() throws Exception; }
    static double median(Run run) throws Exception {
        for(int i=0;i<8;i++) run.run();
        long[] times=new long[21];
        for(int i=0;i<times.length;i++) { long begin=System.nanoTime();run.run();times[i]=System.nanoTime()-begin; }
        Arrays.sort(times);return times[times.length/2]/1_000_000.0;
    }
    public static void main(String[] args) throws Exception {
        contains = args.length > 0 && args[0].equals("--contains");
        System.out.println("posts,terms,mode,matches,sql_like_ms,index_java_ms,index_sql_ms");
        for(int n:new int[]{100,1000,10000}) {
            try(Connection connection=DriverManager.getConnection("jdbc:h2:mem:bench"+n+";QUERY_CACHE_SIZE=0")) {
                db=connection;seed(n);
                for(String[] terms:new String[][]{{"단어3","단어147"},{"여행","코스"}}) for(boolean and:new boolean[]{true,false}) {
                    var expected=like(terms,and);
                    if(!expected.equals(javaIndex(terms,and))||!expected.equals(sqlIndex(terms,and))) throw new AssertionError("Different search results");
                    System.out.printf(Locale.ROOT,"%d,%s,%s,%d,%.4f,%.4f,%.4f%n",n,(terms[0].startsWith("단어")?"rare":"common"),and?"AND":"OR",expected.size(),median(()->like(terms,and)),median(()->javaIndex(terms,and)),median(()->sqlIndex(terms,and)));
                }
            }
        }
    }
}

