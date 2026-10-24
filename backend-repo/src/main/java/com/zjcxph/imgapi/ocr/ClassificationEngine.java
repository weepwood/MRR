package com.zjcxph.imgapi.ocr;

import com.google.re2j.Pattern;
import java.text.Normalizer;
import java.util.*;

/** Scores are rule weights, never probabilities. Raw text remains untouched. */
public class ClassificationEngine {
    public record Rule(Long id,int targetType,String keyword,String matchScope,String matchMode,double weight,
                       boolean negative,boolean required,int priority,boolean enabled) {}
    public record Lexicon(String term,List<String> aliases,List<String> commonOcrErrors) {}
    public record Outcome(Integer targetType,double score,List<Long> matched,List<Long> excluded,boolean autoEligible,String reason) {}
    /** Normalized page text is rule-independent: build raw, corrected and title views once, not per rule. */
    private record PageLine(String raw,String derived,boolean title,double confidence) {}

    public void validate(Rule r) {
        if (r.targetType()<1 || r.targetType()>15 || r.keyword()==null || r.keyword().isBlank() || r.keyword().length()>500
                || !Set.of("TITLE","BODY","ANY").contains(r.matchScope()) || !Set.of("CONTAINS","EXACT","REGEX").contains(r.matchMode())
                || !Double.isFinite(r.weight()) || r.weight()<=0 || r.weight()>1000 || r.priority()<0)
            throw new IllegalArgumentException("分类规则无效");
        if ("REGEX".equals(r.matchMode())) {
            try { Pattern.compile(r.keyword()); }
            catch (com.google.re2j.PatternSyntaxException e) { throw new IllegalArgumentException("分类正则无效"); }
        }
    }
    public Outcome classify(List<Rule> rules,List<Lexicon> lexicon,OcrProtocol.Result page) {
        List<String> corrections=new ArrayList<>();
        Map<String,Set<String>> exactAliases=new HashMap<>();
        for (Lexicon entry:lexicon) {
            String term=normalize(entry.term());
            for (String error:entry.commonOcrErrors()) {
                String normalized=normalize(error);
                if (!normalized.isEmpty()) { corrections.add(normalized); corrections.add(term); }
            }
            Set<String> aliases=exactAliases.computeIfAbsent(term,key->new HashSet<>());
            for (String alias:entry.aliases()) aliases.add(normalize(alias));
        }
        double titleBottom=page.height()*.25;
        List<PageLine> lines=new ArrayList<>(page.lines().size());
        for (var line:page.lines()) {
            String raw=normalize(line.text());
            String derived=raw;
            for (int i=0;i<corrections.size();i+=2) derived=derived.replace(corrections.get(i),corrections.get(i+1));
            lines.add(new PageLine(raw,derived,line.box().get(1)<=titleBottom&&line.text().length()<=64,line.confidence()));
        }
        Map<Integer,Double> scores=new HashMap<>(); Map<Integer,Integer> priorities=new HashMap<>();
        Set<Integer> excludedTypes=new HashSet<>(); Set<Integer> exactTitles=new HashSet<>();
        List<Long> matched=new ArrayList<>(); List<Long> excluded=new ArrayList<>();
        for (Rule rule:rules) {
            if (!rule.enabled()) continue;
            validate(rule);
            boolean exact="EXACT".equals(rule.matchMode()), titleScope="TITLE".equals(rule.matchScope());
            String keyword=normalize(rule.keyword());
            Pattern regex=!exact&&"REGEX".equals(rule.matchMode())?Pattern.compile(rule.keyword()):null;
            Set<String> aliases=exact?exactAliases.getOrDefault(keyword,Set.of()):Set.of();
            boolean hit=false,exactTitle=false;
            for (PageLine line:lines) {
                if ((titleScope&&!line.title())||(!titleScope&&"BODY".equals(rule.matchScope())&&line.title())) continue;
                boolean rawHit=matches(exact,regex,keyword,line.raw());
                if (exact&&!rawHit&&aliases.contains(line.raw())) rawHit=true;
                boolean lineHit=rawHit||matches(exact,regex,keyword,line.derived());
                hit=hit||lineHit;
                exactTitle=exactTitle||(rawHit&&line.title()&&line.confidence()>=.98&&titleScope&&exact);
            }
            if ((rule.negative()&&hit)||(rule.required()&&!hit)) { excludedTypes.add(rule.targetType()); excluded.add(rule.id()); }
            if (!rule.negative()&&hit) {
                scores.merge(rule.targetType(),rule.weight(),Double::sum); priorities.merge(rule.targetType(),rule.priority(),Math::min);
                matched.add(rule.id()); if(exactTitle) exactTitles.add(rule.targetType());
            }
        }
        excludedTypes.forEach(scores::remove);
        if(scores.isEmpty()) return new Outcome(null,0,matched,excluded,false,"NO_MATCH");
        List<Integer> ranked=new ArrayList<>(scores.keySet());
        ranked.sort(Comparator.<Integer>comparingDouble(scores::get).reversed().thenComparingInt(priorities::get).thenComparingInt(Integer::intValue));
        Integer type=ranked.getFirst();
        boolean automatic=ranked.size()==1&&exactTitles.contains(type)&&page.confidence()>=.90;
        return new Outcome(type,scores.get(type),matched,excluded,automatic,
                ranked.size()>1?"CONFLICT":automatic?"EXACT_TITLE":"REVIEW_REQUIRED");
    }
    private boolean matches(boolean exact,Pattern regex,String keyword,String text) {
        if (regex!=null) return regex.matcher(text).find();
        return exact ? text.equals(keyword) : text.contains(keyword);
    }
    private String normalize(String text) { return Normalizer.normalize(text,Normalizer.Form.NFKC).replaceAll("\\s+","").trim(); }
}
