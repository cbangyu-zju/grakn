/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016-2018 Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/agpl.txt>.
 */

package ai.grakn.graql.internal.reasoner.inference;

import ai.grakn.GraknTx;
import ai.grakn.concept.Concept;
import ai.grakn.graql.GetQuery;
import ai.grakn.graql.Graql;
import ai.grakn.graql.QueryBuilder;
import ai.grakn.graql.admin.Answer;
import ai.grakn.test.rule.SampleKBContext;
import ai.grakn.test.kbs.DiagonalKB;
import ai.grakn.test.kbs.DualLinearTransitivityMatrixKB;
import ai.grakn.test.kbs.LinearTransitivityMatrixKB;
import ai.grakn.test.kbs.NguyenKB;
import ai.grakn.test.kbs.PathTreeKB;
import ai.grakn.test.kbs.PathMatrixKB;
import ai.grakn.test.kbs.PathTreeSymmetricKB;
import ai.grakn.test.kbs.TailRecursionKB;
import ai.grakn.test.kbs.TransitivityChainKB;
import ai.grakn.test.kbs.TransitivityMatrixKB;
import java.util.List;

import ai.grakn.util.GraknTestUtil;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static ai.grakn.util.GraqlTestUtil.assertCollectionsEqual;
import static ai.grakn.util.GraqlTestUtil.assertQueriesEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class RecursiveInferenceTest {

    // The recursivity graph is loaded to test if possible, but is unused elsewhere
    @ClassRule
    public static final SampleKBContext recursivityContext = SampleKBContext.load("recursivity-test.gql");

    @ClassRule
    public static final SampleKBContext recursivitySGContext = SampleKBContext.load("recursivity-sg-test.gql");

    @ClassRule
    public static final SampleKBContext recursivityTCContext = SampleKBContext.load("recursivity-tc-test.gql");

    @ClassRule
    public static final SampleKBContext recursivityRSGContext = SampleKBContext.load("recursivity-rsg-test.gql");

    @ClassRule
    public static final SampleKBContext ancestorFriendContext = SampleKBContext.load("ancestor-friend-test.gql");

    @ClassRule
    public static final SampleKBContext transitivityContext = SampleKBContext.load("transitivity-test.gql");

    @ClassRule
    public static final SampleKBContext ancestorContext = SampleKBContext.load("ancestor-test.gql");

    @ClassRule
    public static final SampleKBContext reachabilityContext = SampleKBContext.load("reachability-test.gql");

    @ClassRule
    public static final SampleKBContext sameGenerationContext = SampleKBContext.load("same-generation-test.gql");

    @ClassRule
    public static final SampleKBContext reachabilitySymmetricContext = SampleKBContext.load("reachability-test-symmetric.gql");

    @Before
    public void onStartup() throws Exception {
        assumeTrue(GraknTestUtil.usingTinker());
    }

    /**from Vieille - Recursive Axioms in Deductive Databases p. 192*/
    @Test
    public void testTransitivity() {
        QueryBuilder qb = transitivityContext.tx().graql().infer(false);
        QueryBuilder iqb = transitivityContext.tx().graql().infer(true);
        String queryString = "match ($x, $y) isa R;$x has index 'i'; get $y;";
        String explicitQuery = "match $y has index $ind;" +
                "{$ind val 'j';} or {$ind val 's';} or {$ind val 'v';}; get $y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /*single-directional*/
    /**from Bancilhon - An Amateur's Introduction to Recursive Query Processing Strategies p. 25*/
    @Test
    public void testAncestor() {
        QueryBuilder qb = ancestorContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorContext.tx().graql().infer(true);

        String queryString = "match (ancestor: $X, descendant: $Y) isa Ancestor;$X has name 'aa';" +
                "$Y has name $name;get $Y, $name;";
        String explicitQuery = "match $Y isa person, has name $name;" +
                "{$name val 'aaa';} or {$name val 'aab';} or {$name val 'aaaa';};get $Y, $name;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**as above but both directions*/
    @Test
    public void testAncestorPrime() {
        QueryBuilder qb = ancestorContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorContext.tx().graql().infer(true);

        String queryString = "match ($X, $Y) isa Ancestor;$X has name 'aa'; get $Y;";
        String explicitQuery = "match $Y isa person, has name $name;" +
                "{$name val 'a';} or {$name val 'aaa';} or {$name val 'aab';} or {$name val 'aaaa';};get $Y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testAncestorClosure() {
        QueryBuilder qb = ancestorContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorContext.tx().graql().infer(true);

        String queryString = "match (ancestor: $X, descendant: $Y) isa Ancestor; get;";
        String explicitQuery = "match $Y isa person, has name $nameY; $X isa person, has name $nameX;" +
                "{$nameX val 'a';$nameY val 'aa';} or {$nameX val 'a';$nameY val 'ab';} or" +
                "{$nameX val 'a';$nameY val 'aaa';} or {$nameX val 'a';$nameY val 'aab';} or" +
                "{$nameX val 'a';$nameY val 'aaaa';} or {$nameX val 'aa';$nameY val 'aaa';} or" +
                "{$nameX val 'aa';$nameY val 'aab';} or {$nameX val 'aa';$nameY val 'aaaa';} or " +
                "{$nameX val 'aaa';$nameY val 'aaaa';} or {$nameX val 'c';$nameY val 'ca';}; get $X, $Y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testAncestorClosurePrime() {
        QueryBuilder qb = ancestorContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorContext.tx().graql().infer(true);
        String queryString = "match ($X, $Y) isa Ancestor; get;";
        String explicitQuery = "match $Y isa person, has name $nameY; $X isa person, has name $nameX;" +
                "{$nameX val 'a';$nameY val 'aa';} or " +
                "{$nameX val 'a';$nameY val 'ab';} or {$nameX val 'a';$nameY val 'aaa';} or" +
                "{$nameX val 'a';$nameY val 'aab';} or {$nameX val 'a';$nameY val 'aaaa';} or " +
                "{$nameY val 'a';$nameX val 'aa';} or" +
                "{$nameY val 'a';$nameX val 'ab';} or {$nameY val 'a';$nameX val 'aaa';} or" +
                "{$nameY val 'a';$nameX val 'aab';} or {$nameY val 'a';$nameX val 'aaaa';} or "
                +
                "{$nameX val 'aa';$nameY val 'aaa';} or {$nameX val 'aa';$nameY val 'aab';} or" +
                "{$nameX val 'aa';$nameY val 'aaaa';} or " +
                "{$nameY val 'aa';$nameX val 'aaa';} or {$nameY val 'aa';$nameX val 'aab';} or" +
                "{$nameY val 'aa';$nameX val 'aaaa';} or "
                +
                "{$nameX val 'aaa';$nameY val 'aaaa';} or " +
                "{$nameY val 'aaa';$nameX val 'aaaa';} or "
                +
                "{$nameX val 'c';$nameY val 'ca';} or " +
                "{$nameY val 'c';$nameX val 'ca';}; get $X, $Y;";
        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**from Vieille - Recursive Axioms in Deductive Databases (QSQ approach) p. 186*/
    @Test
    public void testAncestorFriend() {
        QueryBuilder qb = ancestorFriendContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorFriendContext.tx().graql().infer(true);

        String queryString = "match (ancestor: $X, ancestor-friend: $Y) isa Ancestor-friend;$X has name 'a'; $Y has name $name; get $Y, $name;";
        String explicitQuery = "match $Y has name $name;{$name val 'd';} or {$name val 'g';}; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**from Vieille - Recursive Axioms in Deductive Databases (QSQ approach) p. 186*/
    @Test
    public void testAncestorFriendPrime() {
        QueryBuilder qb = ancestorFriendContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorFriendContext.tx().graql().infer(true);

        String queryString = "match ($X, $Y) isa Ancestor-friend;$X has name 'a'; get $Y;";
        String explicitQuery = "match $Y has name $name;{$name val 'd';} or {$name val 'g';}; get $Y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**from Vieille - Recursive Axioms in Deductive Databases (QSQ approach) p. 186*/
    @Test
    public void testAncestorFriend_secondVariant() {
        QueryBuilder qb = ancestorFriendContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorFriendContext.tx().graql().infer(true);

        String queryString = "match (ancestor: $X, ancestor-friend: $Y) isa Ancestor-friend;$Y has name 'd'; get $X;";
        String explicitQuery = "match $X has name $name;" +
                "{$name val 'a';} or {$name val 'b';} or {$name val 'c';}; get $X;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**from Vieille - Recursive Axioms in Deductive Databases (QSQ approach) p. 186*/
    @Test
    public void testAncestorFriend__secondVariantPrime() {
        QueryBuilder qb = ancestorFriendContext.tx().graql().infer(false);
        QueryBuilder iqb = ancestorFriendContext.tx().graql().infer(true);

        String queryString = "match ($X, $Y) isa Ancestor-friend;$Y has name 'd'; get $X;";
        String explicitQuery = "match $X has name $name;" +
                "{$name val 'a';} or {$name val 'b';} or {$name val 'c';}; get $X;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**from Vieille - Recursive Query Processing: The power of logic p. 25*/
    @Test
    public void testSameGeneration(){
        QueryBuilder qb = recursivitySGContext.tx().graql().infer(false);
        QueryBuilder iqb = recursivitySGContext.tx().graql().infer(true);

        String queryString = "match ($x, $y) isa SameGen; $x has name 'a'; get $y;";
        String explicitQuery = "match $y has name $name;{$name val 'f';} or {$name val 'a';};get $y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**from Vieille - Recursive Query Processing: The power of logic p. 18*/
    @Test
    public void testTC() {
        QueryBuilder qb = recursivityTCContext.tx().graql().infer(false);
        QueryBuilder iqb = recursivityTCContext.tx().graql().infer(true);

        String queryString = "match ($x, $y) isa N-TC; $y has index 'a'; get $x;";
        String explicitQuery = "match $x has index 'a2'; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testReachability(){
        QueryBuilder qb = reachabilityContext.tx().graql().infer(false);
        QueryBuilder iqb = reachabilityContext.tx().graql().infer(true);

        String queryString = "match (reach-from: $x, reach-to: $y) isa reachable; get;";
        String explicitQuery = "match $x has index $indX;$y has index $indY;" +
                "{$indX val 'a';$indY val 'b';} or" +
                "{$indX val 'b';$indY val 'c';} or" +
                "{$indX val 'c';$indY val 'c';} or" +
                "{$indX val 'c';$indY val 'd';} or" +
                "{$indX val 'a';$indY val 'c';} or" +
                "{$indX val 'b';$indY val 'd';} or" +
                "{$indX val 'a';$indY val 'd';};get $x, $y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testReachabilitySymmetric(){
        QueryBuilder qb = reachabilitySymmetricContext.tx().graql().infer(false);
        QueryBuilder iqb = reachabilitySymmetricContext.tx().graql().infer(true);

        String queryString = "match ($x, $y) isa reachable;$x has index 'a';get $y;";
        String explicitQuery = "match $y has index $indY;" +
                "{$indY val 'a';} or {$indY val 'b';} or {$indY val 'c';} or {$indY val 'd';};get $y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /** test 6.1 from Cao p 71*/
    @Test
    public void testDualLinearTransitivityMatrix(){
        final int N = 5;
        SampleKBContext kb = DualLinearTransitivityMatrixKB.context(N, N);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (Q1-from: $x, Q1-to: $y) isa Q1; $x has index 'a0'; get $y;";
        String explicitQuery = "match $y isa a-entity or $y isa end; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**
     *  test 6.3 from Cao p 75*/
    @Test
    public void testTailRecursion(){
        final int N = 10;
        final int M = 5;
        SampleKBContext kb = TailRecursionKB.context(N, M);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (P-from: $x, P-to: $y) isa P; $x has index 'a0'; get $y;";
        String explicitQuery = "match $y isa b-entity; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**test3 from Nguyen (similar to test 6.5 from Cao)
     * N(x, y) :- R(x, y)
     * N(x, y) :- P(x, z), N(z, w), Q(w, y)
     *
     *
     *   c -- P -- d -- R -- e -- Q -- a0
     *     \                        /
     *         P               Q
     *      \    \          /
     *                b0   --  Q  --   a1
     *        \                     /
     *          P              Q
     *             \        /
     *                b1   --  Q  --   a2
     *                            .
     *                         .
     *                      .
     *                bN   --  Q --    aN+1
     */
    @Test
    public void testNguyen(){
        final int N = 9;
        SampleKBContext kb = NguyenKB.context(N);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (N-rA: $x, N-rB: $y) isa N; $x has index 'c'; get $y;";
        String explicitQuery = "match $y isa a-entity; get;";

        List<Answer> answers = iqb.<GetQuery>parse(queryString).execute();
        List<Answer> explicitAnswers = qb.<GetQuery>parse(explicitQuery).execute();
        assertCollectionsEqual(answers, explicitAnswers);
    }

    /**test 6.6 from Cao p.76*/
    @Test
    public void testSameGenerationCao(){
        QueryBuilder qb = sameGenerationContext.tx().graql().infer(false);
        QueryBuilder iqb = sameGenerationContext.tx().graql().infer(true);

        String queryString = "match ($x, $y) isa SameGen;$x has name 'ann';get $y;";
        String explicitQuery = "match $y has name $name;" +
                "{$name val 'ann';} or {$name val 'bill';} or {$name val 'peter';};get $y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**test 6.9 from Cao p.82*/
    @Test
    public void testLinearTransitivityMatrix(){
        final int N = 5;
        final int M = 5;
        SampleKBContext kb = LinearTransitivityMatrixKB.context(N, M);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (P-from: $x, P-to: $y) isa P;$x has index 'a'; get $y;";
        String explicitQuery = "match $y isa a-entity; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**test 6.10 from Cao p. 82*/
    @Test
    public void testPathTree(){
        final int N = 3;
        SampleKBContext kb = PathTreeKB.context(N, 3);
        GraknTx tx = kb.tx();
        QueryBuilder qb = tx.graql().infer(false);
        QueryBuilder iqb = tx.graql().infer(true);

        Concept a0 = getConcept(tx, "index", "a0");

        String queryString = "match (path-from: $x, path-to: $y) isa path;" +
                "$x has index 'a0';" +
                "get $y;";
        String explicitQuery = "match $y isa vertex; get;";


        List<Answer> answers = iqb.<GetQuery>parse(queryString).execute();
        List<Answer> explicitAnswers = qb.<GetQuery>parse(explicitQuery).execute();

        assertCollectionsEqual(answers, explicitAnswers);
    }

    @Test
    public void testPathTreePrime(){
        final int N = 3;
        SampleKBContext kb = PathTreeKB.context(N, 3);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match ($x, $y) isa path;$x has index 'a0'; get $y;";
        String explicitQuery = "match $y isa vertex; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testPathSymmetric(){
        final int N = 2;
        SampleKBContext kb = PathTreeSymmetricKB.context(N, 3);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match ($x, $y) isa path;$x has index 'a0'; get $y;";
        String explicitQuery = "match {$y isa vertex;} or {$y isa start-vertex;}; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    /*modified test 6.10 from Cao p. 82*/
    public void testPathMatrix(){
        final int N = 3;
        SampleKBContext kb = PathMatrixKB.context(N, N);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (path-from: $x, path-to: $y) isa path;$x has index 'a0'; get $y;";
        String explicitQuery = "match $y isa vertex; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    /*modified test 6.10 from Cao p. 82*/
    public void testPathMatrixPrime(){
        final int N = 3;
        SampleKBContext kb = PathMatrixKB.context(N, N);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match ($x, $y) isa path;$x has index 'a0'; $y has index $ind;get $y, $ind;";
        String explicitQuery = "match $y isa vertex;$y has index $ind; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    /**from Abiteboul - Foundations of databases p. 312/Cao test 6.14 p. 89*/
    @Test
    public void testReverseSameGeneration(){
        QueryBuilder qb = recursivityRSGContext.tx().graql().infer(false);
        QueryBuilder iqb = recursivityRSGContext.tx().graql().infer(true);

        String queryString = "match (RSG-from: $x, RSG-to: $y) isa RevSG;$x has name 'a'; get $y;";
        String explicitQuery = "match $y isa person, has name $name;" +
                "{$name val 'b';} or {$name val 'c';} or {$name val 'd';};get $y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }
    @Test
    public void testReverseSameGeneration2() {
        QueryBuilder qb = recursivityRSGContext.tx().graql().infer(false);
        QueryBuilder iqb = recursivityRSGContext.tx().graql().infer(true);

        String queryString = "match (RSG-from: $x, RSG-to: $y) isa RevSG; get;";
        String explicitQuery = "match $x has name $nameX;$y has name $nameY;" +
                "{$nameX val 'a';$nameY val 'b';} or {$nameX val 'a';$nameY val 'c';} or" +
                "{$nameX val 'a';$nameY val 'd';} or {$nameX val 'm';$nameY val 'n';} or" +
                "{$nameX val 'm';$nameY val 'o';} or {$nameX val 'p';$nameY val 'm';} or" +
                "{$nameX val 'g';$nameY val 'f';} or {$nameX val 'h';$nameY val 'f';} or" +
                "{$nameX val 'i';$nameY val 'f';} or {$nameX val 'j';$nameY val 'f';} or" +
                "{$nameX val 'f';$nameY val 'k';};get $x, $y;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testTransitiveChain(){
        final int N = 10;
        SampleKBContext kb = TransitivityChainKB.context(N);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (Q-from: $x, Q-to: $y) isa Q;$x has index 'a'; get $y;";
        String explicitQuery = "match $y isa a-entity; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testTransitiveMatrix(){
        final int N = 5;
        SampleKBContext kb = TransitivityMatrixKB.context(N, N);
        QueryBuilder qb = kb.tx().graql().infer(false);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (Q-from: $x, Q-to: $y) isa Q;$x has index 'a'; get $y;";
        String explicitQuery = "match $y isa a-entity; get;";

        assertQueriesEqual(iqb.parse(queryString), qb.parse(explicitQuery));
    }

    @Test
    public void testDiagonal(){
        final int N = 10;
        SampleKBContext kb = DiagonalKB.context(N, N);
        QueryBuilder iqb = kb.tx().graql().infer(true);

        String queryString = "match (rel-from: $x, rel-to: $y) isa diagonal; get;";

        assertEquals(iqb.<GetQuery>parse(queryString).execute().size(), 64);
    }

    private Concept getConcept(GraknTx graph, String typeName, Object val){
        return graph.graql().match(Graql.var("x").has(typeName, val).admin()).get("x").findAny().orElse(null);
    }
}
