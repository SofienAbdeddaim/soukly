## Ticket

<!-- ID et lien de la fiche, par exemple SETUP-03. Un ticket = une PR. -->

## Avant / Après

**Avant :**

**Après :**

<!-- Pour le front : captures FR et AR (RTL). -->

## Comment tester

<!-- Commandes et étapes pour vérifier la PR en local. -->

## Écarts avec le ticket et points « à confirmer »

<!-- Ce qui diffère de la fiche, et le résultat des vérifications dans la documentation officielle. -->

## Definition of Done

- [ ] Critères Gherkin du ticket vérifiés
- [ ] Lint, tests et build verts en local pour ce qui est touché (commandes du README)
- [ ] CI verte (`ci-ok`)
- [ ] Textes dans les 3 langues (fr, ar, en) et rendu RTL vérifié, ou sans objet
- [ ] États de chargement, vide, erreur et accessibilité traités, ou sans objet
- [ ] Migrations Flyway compatibles avec la version précédente, ou sans objet
- [ ] Contrats d'événements documentés dans `docs/events/`, ou sans objet
- [ ] Aucun secret, jeton ni donnée personnelle dans le code ou les logs
- [ ] README à jour si les commandes ou la configuration changent
- [ ] Aucune modification hors périmètre
