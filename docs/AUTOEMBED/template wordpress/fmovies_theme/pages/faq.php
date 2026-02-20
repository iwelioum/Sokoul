<?php
/**
 * Template Name: Faq
 * 
 * A custom page template for Faq
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

get_header(); ?>

<div class="container mt-4">
	<section class="bl">
        <div class="heading"> 
            <?php the_title( '<h2>', '</h2>' ); ?>
            <div class="clearfix"></div> 
		</div><!-- #heading -->
		<div class="content">
			<div class="accordion" id="faqExample">
				<div class="card">
					<div class="card-header p-2" id="headingOne">
						<h5 class="mb-0">
							<button class="btn btn-link" type="button" data-toggle="collapse" data-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">
								Is it legal to watch movies on <?php bloginfo( 'name' ); ?> ?
							</button>
						</h5>
					</div>
					
					<div id="collapseOne" class="collapse show" aria-labelledby="headingOne" data-parent="#faqExample">
						<div class="card-body">
							Absolutely, Watching is totally legal. We also do not host the movies, we link to them.
						</div>
					</div>
				</div>
				<div class="card">
					<div class="card-header p-2" id="headingTwo">
						<h5 class="mb-0">
							<button class="btn btn-link collapsed" type="button" data-toggle="collapse" data-target="#collapseTwo" aria-expanded="false" aria-controls="collapseTwo">
								I can't find my favorite movies, what can I do?
							</button>
						</h5>
					</div>
					<div id="collapseTwo" class="collapse" aria-labelledby="headingTwo" data-parent="#faqExample">
						<div class="card-body">
							Please use the Request form and we will upload these movies for you.
						</div>
					</div>
				</div>
				<div class="card">
					<div class="card-header p-2" id="headingThree">
						<h5 class="mb-0">
							<button class="btn btn-link collapsed" type="button" data-toggle="collapse" data-target="#collapseThree" aria-expanded="false" aria-controls="collapseThree">
								What are the differences between Premium and other servers?
							</button>
						</h5>
					</div>
					<div id="collapseThree" class="collapse" aria-labelledby="headingThree" data-parent="#faqExample">
						<div class="card-body">
							Premium are our main servers and don't have any ads, loading faster, multiple quality, hls format no buffering. Other servers may come with ads, we don't control them. Sometimes if Premium does not work, please try with backup servers, video in all servers are same.
						</div>
					</div>
				</div>
				<div class="card">
					<div class="card-header p-2" id="headingThree">
						<h5 class="mb-0">
							<button class="btn btn-link collapsed" type="button" data-toggle="collapse" data-target="#collapseThree" aria-expanded="false" aria-controls="collapseThree">
								How to change quality of the movie i am watching ?
							</button>
						</h5>
					</div>
					<div id="collapseThree" class="collapse" aria-labelledby="headingThree" data-parent="#faqExample">
						<div class="card-body">
							Most of movies have some quality options (1080p, 720p, 480p, 360p). You can choose which you want. Some of them just have 720p and 360p and it will display HD/SD badge.
						</div>
					</div>
				</div>
			</div>
			<div class="clearfix"></div>
		</div><!-- /content -->
	</section><!-- /section -->
</div><!-- /container-->
<?php get_footer(); ?>




