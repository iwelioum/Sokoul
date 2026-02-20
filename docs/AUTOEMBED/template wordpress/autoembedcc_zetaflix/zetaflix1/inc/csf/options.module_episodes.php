<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('TV Shows > Episodes'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'episodestitle',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Episodes'),
                'subtitle'    => __z('Add title to show')
            ),
            array(
                'id'    => 'episodesmodcontrol',
                'type'  => 'checkbox',
                'title' => __z('Module Control'),
                'subtitle'  => __z('Check to enable option.'),
                'options' => array(
                    'slider' => __z('Activate Module'),
                ),
                'default'=> array('slider')
            ),
            array(
                'id'    => 'episodesmdisplay',
                'type'  => 'radio',
                'title' => __z('Display As'),
                'subtitle' => __z('Select style of this module'),
                'options' => array(
                    'slider' => __z('Slider'),
                    'grid'  => __z('Grid')
                ),
                'default' => 'slider'
            ),			
            array(
                'id'      => 'episodesitems',
                'type'    => 'text',
                'title'   => __z('Slide Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('episodesmdisplay','==','slider'),
            ),
            array(
                'id'      => 'episodesgitems',
                'type'    => 'text',
                'title'   => __z('Grid Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('episodesmdisplay','==','grid'),
			),
            array(
                'id'      => 'episodesmodorderby',
                'type'    => 'select',
                'title'   => __z('Order by'),
                'subtitle' => __z('Order items for this module'),
                'default' => 'date',
                'options' => array(
                    'date'     => __z('Post date'),
                    'title'    => __z('Post title'),
                    'modified' => __z('Last modified'),
                    'rand'     => __z('Random entry'),
                )
            ),
            array(
                'id'    => 'episodesposter',
                'type'  => 'radio',
                'title' => __z('Poster Style'),
                'options' => array(
                    'default'  => __z('Default'),
                    'horizontal' => __z('Horizontal')
                ),
                'default' => 'horizontal',
                'dependency' => array('episodesmdisplay','==','slider'),
            ),
            array(
                'id'    => 'episodessource',
                'type'  => 'radio',
                'title' => __z('Poster Source'),
                'options' => array(
                    'inherittv'    => __z('Inherit from TV Show'),
                    'inheritss'    => __z('Inherit from Season'),
                    'default' => __z('Default post image'),
                ),
                'default' => 'default',
            )
        )
    )
);
