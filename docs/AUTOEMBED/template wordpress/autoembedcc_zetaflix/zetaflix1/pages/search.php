<header>
	<h1><?php _z('Results found:');  ?> <?php echo get_search_query(); ?></h1>
</header>
<div class="search-page">
	<div class="search_page_form">
		<form method="get" id="searchformpage" action="<?php echo esc_url( home_url() ); ?>">
			<input type="text" placeholder="<?php _z('Search...'); ?>" name="s" id="s" value="<?php echo get_search_query(); ?>">
			<button type="submit"><span class="fas fa-search"></span></button>
		</form>
	</div>

<?php
	if (have_posts()) :while (have_posts()) : the_post();
	$zt_date = new DateTime(zeta_get_postmeta('air_date'));
	$zt_player	= get_post_meta($post->ID, 'repeatable_fields', true);
?>
	<div class="result-item">
		<article>
			<div class="image">
				<div class="thumbnail animation-2">
					<a href="<?php the_permalink(); ?>">
					<?php if(get_post_type() == 'episodes') { ?>
					<img src="<?php echo omegadb_get_backdrop($post->ID, 'w92'); ?>" alt="<?php the_title(); ?>" />
					<?php } else { ?>
					<img src="<?php echo omegadb_get_poster($post->ID,'thumbnail','zt_poster','w92'); ?>" alt="<?php the_title(); ?>" />
					<?php } ?>
					<span class="<?php echo get_post_type(); ?>">
					<?php
					// Get post types
					if($d = get_post_type() == 'movies') { _z('Movie'); }
					if($d = get_post_type() == 'tvshows') { _z('TV'); }
					if($d = get_post_type() == 'post') { _z('Post'); }
					if($d = get_post_type() == 'episodes') { _z('Episode'); }
					if($d = get_post_type() == 'seasons') { _z('Season'); }
					?>
					</span>
					</a>
				</div>
			</div>
			<div class="details">
				<div class="title">
					<a href="<?php the_permalink(); ?>"><?php the_title(); ?></a>
				</div>
				<div class="meta">
				<?php if($rt = zeta_get_postmeta('imdbRating')) { echo '<span class="rating">IMDb '. $rt .'</span>'; } ?>
				<?php if( get_post_type() == 'episodes') { if($d = $zt_date) { echo '<span class="year">', $d->format(ZETA_TIME), '</span>'; } } ?>
				<?php if($yr = $tms = strip_tags( $tms = get_the_term_list( $post->ID, 'ztyear'))) { echo '<span class="year">'. $yr .'</span>'; } ?>
				<?php $i=0; if ($zt_player) : foreach ( $zt_player as $field ) { if($i==2) break; if(zeta_isset($field,'idioma')) { ?>
				<span class="flag" style="background-image: url(<?php echo ZETA_URI, '/assets/img/flags/',zeta_isset($field,'idioma'),'.png'; ?>)"></span>
				<?php } $i++; } endif; ?>
				</div>
				<div class="contenido">
					<p><?php zt_content_alt('200'); ?></p>
				</div>
			</div>
		</article>
	</div>
<?php endwhile; else: ?>
<div class="no-result animation-2">
	<h2><?php _z('No results to show with'); ?> <span><?php echo get_search_query(); ?></span></h2>
	<strong><?php _z('Suggestions'); ?>:</strong>
	<ul>
		<li><?php _z('Make sure all words are spelled correctly.'); ?></li>
		<li><?php _z('Try different keywords.'); ?></li>
		<li><?php _z('Try more general keywords.'); ?></li>
	</ul>
</div>
<?php endif; ?>
</div>
