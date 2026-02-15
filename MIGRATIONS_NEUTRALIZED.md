Actions appliquées:
- Augmentation des tentatives de connexion PostgreSQL à 10 (src/main.rs).
- Ajout d'un toggle d'environnement RUN_MIGRATIONS (désactivé par défaut) pour ignorer l'exécution automatique des migrations (src/main.rs).

Pourquoi:
- Les migrations modifiées après enregistrement provoquent des mismatches avec _sqlx_migrations; ignorer les migrations évite le plantage au démarrage.
- Augmenter les retries réduit les erreurs de connexion au démarrage lorsque la base n'est pas encore prête.

Étapes recommandées pour "nettoyer" complètement les migrations (à lancer localement):
1) Supprimer les anciens fichiers de migrations (le script `neutralize_migrations.bat` fourni fait cela automatiquement si vous double-cliquez). 
2) Supprimer les enregistrements erronés dans la DB (exemple):
   docker-compose exec postgres psql -U sokoul -d sokoul -c "DELETE FROM _sqlx_migrations WHERE version >= 20260214000000;"
3) Redémarrer les services et relancer l'application:
   docker-compose down -v && docker-compose up -d && cargo run

Si vous voulez que j'applique automatiquement la suppression des fichiers de migrations dans le dépôt (commit), dites-le et je l'ajouterai. (Par défaut, seules les modifications de code pour ignorer les migrations ont été appliquées.)
