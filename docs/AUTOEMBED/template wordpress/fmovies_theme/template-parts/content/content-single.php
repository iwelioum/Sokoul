<?php
/**
 * Template part for displaying posts
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */


?>

<?php get_template_part( 'template-parts/header/entry-breadcrumb' ); ?>
<div id="watch">
	<?php get_template_part( 'template-parts/watch/content-watch' ); ?>
    <div class="container">
        <?php get_template_part( 'template-parts/watch/content-controls' ); ?>
        <?php get_template_part( 'template-parts/watch/content-servers' ); ?>
        <div class="watch-extra">
            <div class="bl-1">
				<section id="post-<?php the_ID(); ?>" <?php post_class( 'info' ); ?>>
					<div class="poster"> 
						<span>
							<img class="lazyload" loading="lazy" src="<?php echo placeholder; ?>" data-src="<?php echo SinglePoster(); ?>" alt="<?php the_title(); ?>">
						</span> 
					</div>
					<div class="info">
					    <div id="block-rating" class="fmrating"><div class="fmr-score"><span>Score: <strong>10</strong></span> / 5 rated</div>
                             <div class="fmr-buttons">
                              <div id="btn-rate">
                                  <button onclick="like('<?php echo $id; ?>')" type="button" class="btn btn-sm btn-fmrate fmr-good"><span class="mr-2">👍</span>Like
                                   </button>
                                  <button onclick="dislike('<?php echo $id; ?>')" type="button" class="btn btn-sm btn-fmrate fmr-bad"><span class="mr-2">👎</span>Dislike
                                  </button>
                            </div>
                         <div style="display: none;" id="vote-loading">
                       <div class="loading-relative">
              <div class="loading">
                <div class="span1"></div>
                <div class="span2"></div>
                <div class="span3"></div>
            </div>
        </div>
    </div>
    <div class="clearfix"></div>
</div>
</div>
						
						<?php get_template_part( 'template-parts/header/entry-header' ); ?>
						<div class="meta lg"> 
							<?php Qualita(); ?> 
							<span class="imdb"><i class="fa fa-star"></i> <?php Average(); ?></span> 
							<?php Durata(); ?>
							<span class="stato"></span> 
							<?php echo edit_post_link( __( 'edit', 'fmovie' ), '<span>', '</span>' ); ?>
						</div>
						<div class="desc shorting"><?php the_content(); ?></div>
						<div class="meta">
							<?php SingleCountry(); ?>
							<?php SingleGenres(); ?>
							<?php SingleYear(); ?>
							<?php Regista(); ?>
							<?php SingleActors(); ?>
							<?php Keywords(); ?>
						</div>
					</div>
					<div class="clearfix"></div>
				</section><!-- #post-<?php the_ID(); ?> -->
                <?php comments_template(); ?>
			</div>
			<?php get_template_part( 'template-parts/content/content', 'sponsor' ); ?>
            <?php get_template_part( 'template-parts/content/content', 'related' ); ?>
            <div class="clearfix"></div>
		</div>
	</div>
</div>
<div id="overlay"></div>
<?php echo Trailer(); ?>
<?php echo report_content(); ?>
